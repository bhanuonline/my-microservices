package com.example.productquery.admin;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.elasticsearch.indices.update_aliases.AddAction;
import co.elastic.clients.elasticsearch.indices.update_aliases.RemoveAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.productquery.admin.AliasBootstrap.ALIAS;

/**
 * The zero-downtime reindex pattern. Interviewer-grade.
 *
 * Operator flow:
 *
 *   POST /admin/products/reindex?toVersion=v2
 *
 *     1. Create products_v2 (empty — Spring Data has already pushed the
 *        latest @Document mapping, but a fresh create picks up any mapping
 *        change ops hasn't deployed yet).
 *     2. Server-side _reindex: copy every doc from products_v1 → products_v2.
 *        Runs inside ES, no app traffic — scales to millions of docs.
 *     3. ATOMIC alias flip via indices.update_aliases:
 *          remove: products → products_v1
 *          add:    products → products_v2
 *        Both actions commit together — no window where the alias is unresolved.
 *     4. (optional) Delete products_v1.
 *
 * Clients never notice. Writes land on v2 immediately; in-flight reads either
 * complete against v1 (which still exists) or get v2 (fresh mapping).
 *
 * Caveats interviewers probe on:
 *   - Writes DURING the reindex land on v1 only — they're not auto-replayed to v2.
 *     Mitigation: enable dual-write OR do a second reindex sweep after the flip.
 *     Simplest in prod: pause the Kafka projector during reindex (consumer group
 *     offset preserved), reindex, flip, resume. Our topic replay handles any gap.
 *   - _reindex is NOT atomic — if it fails halfway, v2 is partial. Mitigation:
 *     delete v2 and retry; alias still points at v1 so clients unaffected.
 */
@RestController
@RequestMapping("/admin/products")
public class ReindexAdminController {

    private static final Logger log = LoggerFactory.getLogger(ReindexAdminController.class);
    private static final String INDEX_PREFIX = "products_";

    private final ElasticsearchClient client;

    public ReindexAdminController(ElasticsearchClient client) {
        this.client = client;
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex(
            @RequestParam String toVersion,
            @RequestParam(defaultValue = "false") boolean deleteOld) {

        String target = INDEX_PREFIX + toVersion;              // e.g. products_v2
        try {
            // Resolve current index(es) behind the alias so we know what to copy FROM
            // and what to detach when we flip.
            Set<String> current = resolveCurrentIndices();
            if (current.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "no_source_index",
                        "message", "alias '" + ALIAS + "' resolves to nothing — nothing to reindex"
                ));
            }
            if (current.contains(target)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "target_is_current",
                        "message", "target index " + target + " is already behind the alias"
                ));
            }

            // 1. Create target (idempotent — skip if it already exists; operator
            //    is retrying after a half-finished run).
            boolean targetExists = client.indices().exists(e -> e.index(target)).value();
            if (!targetExists) {
                client.indices().create(c -> c.index(target));
                log.info("created {}", target);
            }

            // 2. Server-side copy. Blocking call here for simplicity; use
            //    waitForCompletion=false in production for a task you poll.
            long reindexed = 0;
            for (String source : current) {
                final String src = source;
                ReindexResponse r = client.reindex(req -> req
                        .source(s -> s.index(src))
                        .dest(d -> d.index(target))
                        .refresh(true)
                );
                reindexed += r.total() != null ? r.total() : 0;
                log.info("reindexed {} docs: {} → {}", r.total(), src, target);
            }

            // 3. Atomic flip via indices.update_aliases actions array.
            List<Action> actions = new ArrayList<>();
            for (String old : current) {
                actions.add(Action.of(a -> a.remove(RemoveAction.of(r -> r.index(old).alias(ALIAS)))));
            }
            actions.add(Action.of(a -> a.add(AddAction.of(ad -> ad.index(target).alias(ALIAS)))));
            client.indices().updateAliases(u -> u.actions(actions));
            log.info("alias '{}' flipped: {} → [{}]", ALIAS, current, target);

            // 4. Optional cleanup of old index(es).
            List<String> deleted = new ArrayList<>();
            if (deleteOld) {
                for (String old : current) {
                    client.indices().delete(d -> d.index(old));
                    deleted.add(old);
                    log.info("deleted old index {}", old);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "newIndex", target,
                    "oldIndices", current,
                    "reindexedDocs", reindexed,
                    "deletedOld", deleted
            ));
        } catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            log.error("reindex failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "reindex_failed",
                    "message", e.getMessage()
            ));
        }
    }

    private Set<String> resolveCurrentIndices() throws IOException {
        try {
            GetAliasResponse r = client.indices().getAlias(a -> a.name(ALIAS));
            return r.result().keySet();
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException notFound) {
            return Set.of();
        }
    }
}
