package com.example.productquery.admin;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Guarantees the alias/concrete-index invariant on startup:
 *
 *   products                 (alias)      ← what @Document resolves to
 *     └── products_v1        (concrete index — current write target)
 *
 * Why bootstrap it in code instead of a one-off curl?
 *   - Fresh clusters (CI, local, prod rebuild) need the alias before the
 *     first write, otherwise Spring Data auto-creates "products" as a
 *     real index — then alias creation fails ("name already in use").
 *   - Running in prod with a stale alias config (someone deleted it in Kibana)
 *     self-heals on next restart.
 *
 * Idempotent — safe to run on every boot.
 */
@Component
public class AliasBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AliasBootstrap.class);

    static final String ALIAS = "products";
    static final String BOOTSTRAP_INDEX = "products_v1";

    private final ElasticsearchClient client;

    public AliasBootstrap(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Flow:
     *   1. Alias exists? → nothing to do; return.
     *   2. Concrete index 'products_v1' missing? → create it (empty).
     *      Spring Data pushes the @Document mapping on first save because
     *      the alias routes writes here.
     *   3. Attach alias 'products' → 'products_v1'.
     *
     * If a concrete index literally named 'products' already exists (someone
     * ran a save without the bootstrap), the putAlias call will fail —
     * logged at WARN, startup continues. Operator fix: delete that index.
     */
    @PostConstruct
    public void ensureAlias() {
        try {
            if (aliasExists()) {
                log.info("alias '{}' already present — skipping bootstrap", ALIAS);
                return;
            }
            createIndexIfMissing(BOOTSTRAP_INDEX);
            putAlias(BOOTSTRAP_INDEX, ALIAS);
            log.info("bootstrapped alias '{}' → '{}'", ALIAS, BOOTSTRAP_INDEX);
        } catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            // Don't fail startup — alias may be managed externally (Kibana, Terraform).
            log.warn("alias bootstrap skipped: {}", e.getMessage());
        }
    }

    private boolean aliasExists() throws IOException {
        try {
            GetAliasResponse r = client.indices().getAlias(a -> a.name(ALIAS));
            return !r.result().isEmpty();
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException notFound) {
            return false;
        }
    }

    private void createIndexIfMissing(String index) throws IOException {
        boolean exists = client.indices().exists(e -> e.index(index)).value();
        if (exists) return;
        // Minimal creation — Spring Data will push the mapping on first save
        // because the alias will route to this index.
        client.indices().create(c -> c.index(index));
    }

    private void putAlias(String index, String alias) throws IOException {
        client.indices().putAlias(PutAliasRequest.of(p -> p.index(index).name(alias)));
    }
}
