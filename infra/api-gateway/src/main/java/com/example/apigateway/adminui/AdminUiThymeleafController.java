package com.example.apigateway.adminui;

import com.example.apigateway.apikey.ApiKeyAdminController;
import com.example.apigateway.apikey.ApiKeyStore;
import com.example.apigateway.apikey.ApiKeyProperties;
import com.example.apigateway.dynamicroutes.RouteAdminController;
import com.example.apigateway.dynamicroutes.RouteAuditRepository;
import com.example.apigateway.dynamicroutes.RouteValidator;
import com.example.apigateway.dynamicroutes.RouteValidator.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Server-rendered admin UI via Thymeleaf's REACTIVE view resolver (works with
 * Spring Cloud Gateway's WebFlux stack — no servlet dependency).
 *
 * Active when gateway.admin.ui=THYMELEAF. Full-page reloads; auth via HTTP Basic
 * (added by GatewaySecurityConfig when ui=THYMELEAF).
 *
 * Paths (all under /admin/ui):
 *   GET  /              → redirect to /routes
 *   GET  /routes        → route list + inline create form
 *   POST /routes        → create route (form submit) → redirect back
 *   GET  /routes/{id}/edit → edit page
 *   POST /routes/{id}/update → update (form submit)
 *   POST /routes/{id}/delete → delete (form submit, method-override via POST)
 *   POST /routes/refresh    → force RefreshRoutesEvent
 *   GET  /audit         → audit log (last 100)
 *   GET  /apikeys       → API keys page (create form only — list needs new store method)
 *   POST /apikeys       → create key (form submit) → shows raw key page
 */
@Controller
@RequestMapping("/admin/ui")
@ConditionalOnProperty(prefix = "gateway.admin", name = "ui", havingValue = "THYMELEAF")
public class AdminUiThymeleafController {

    private static final Logger log = LoggerFactory.getLogger(AdminUiThymeleafController.class);

    private static final TypeReference<List<PredicateDefinition>> PREDICATE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<FilterDefinition>> FILTER_LIST = new TypeReference<>() {};

    private final RouteDefinitionRepository routeRepo;
    private final RouteAuditRepository auditRepo;
    private final RouteValidator validator;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final ObjectProvider<ApiKeyStore> apiKeyStoreProvider;
    private final ObjectProvider<ApiKeyProperties> apiKeyPropsProvider;

    public AdminUiThymeleafController(RouteDefinitionRepository routeRepo,
                                      RouteAuditRepository auditRepo,
                                      RouteValidator validator,
                                      ApplicationEventPublisher events,
                                      ObjectMapper mapper,
                                      ObjectProvider<ApiKeyStore> apiKeyStoreProvider,
                                      ObjectProvider<ApiKeyProperties> apiKeyPropsProvider) {
        this.routeRepo = routeRepo;
        this.auditRepo = auditRepo;
        this.validator = validator;
        this.events = events;
        this.mapper = mapper;
        this.apiKeyStoreProvider = apiKeyStoreProvider;
        this.apiKeyPropsProvider = apiKeyPropsProvider;
    }

    // ────────────────────────────────────────────────────────────────
    // Routes
    // ────────────────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public Mono<String> index() {
        return Mono.just("redirect:/admin/ui/routes");
    }

    @GetMapping("/routes")
    public Mono<String> routes(Model model,
                               @RequestParam(required = false) String created,
                               @RequestParam(required = false) String updated,
                               @RequestParam(required = false) String deleted,
                               @RequestParam(required = false) String error) {
        model.addAttribute("active", "routes");
        model.addAttribute("created", created);
        model.addAttribute("updated", updated);
        model.addAttribute("deleted", deleted);
        model.addAttribute("error", error);
        return routeRepo.getRouteDefinitions()
                .collectList()
                .doOnNext(list -> model.addAttribute("routes", list))
                .thenReturn("routes/list");
    }

    @PostMapping("/routes")
    public Mono<String> createRoute(@ModelAttribute RouteForm form) {
        RouteDefinition def;
        try {
            def = form.toDefinition(mapper);
            validator.validate(def);
        } catch (ValidationException | IllegalArgumentException e) {
            return Mono.just("redirect:/admin/ui/routes?error=" + enc(e.getMessage()));
        }
        return routeRepo.save(Mono.just(def))
                .doOnSuccess(v -> events.publishEvent(new RefreshRoutesEvent(this)))
                .thenReturn("redirect:/admin/ui/routes?created=" + enc(def.getId()))
                .onErrorResume(err -> Mono.just("redirect:/admin/ui/routes?error=" + enc(err.getMessage())));
    }

    @GetMapping("/routes/{id}/edit")
    public Mono<String> editRoute(@PathVariable String id, Model model) {
        model.addAttribute("active", "routes");
        return routeRepo.getRouteDefinitions()
                .filter(r -> id.equals(r.getId()))
                .next()
                .flatMap(r -> {
                    try {
                        model.addAttribute("routeId", r.getId());
                        model.addAttribute("uri", r.getUri().toString());
                        model.addAttribute("order", r.getOrder());
                        model.addAttribute("predicatesJson",
                                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(r.getPredicates()));
                        model.addAttribute("filtersJson", r.getFilters() == null || r.getFilters().isEmpty()
                                ? "[]"
                                : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(r.getFilters()));
                        return Mono.just("routes/edit");
                    } catch (Exception ex) {
                        return Mono.just("redirect:/admin/ui/routes?error=" + enc("Serialize failed: " + ex.getMessage()));
                    }
                })
                .switchIfEmpty(Mono.just("redirect:/admin/ui/routes?error=" + enc("Route not found: " + id)));
    }

    @PostMapping("/routes/{id}/update")
    public Mono<String> updateRoute(@PathVariable String id, @ModelAttribute RouteForm form) {
        form.setId(id);
        RouteDefinition def;
        try {
            def = form.toDefinition(mapper);
            validator.validate(def);
        } catch (ValidationException | IllegalArgumentException e) {
            return Mono.just("redirect:/admin/ui/routes/" + enc(id) + "/edit?error=" + enc(e.getMessage()));
        }
        return routeRepo.save(Mono.just(def))
                .doOnSuccess(v -> events.publishEvent(new RefreshRoutesEvent(this)))
                .thenReturn("redirect:/admin/ui/routes?updated=" + enc(id))
                .onErrorResume(err -> Mono.just("redirect:/admin/ui/routes?error=" + enc(err.getMessage())));
    }

    @PostMapping("/routes/{id}/delete")
    public Mono<String> deleteRoute(@PathVariable String id) {
        return routeRepo.delete(Mono.just(id))
                .doOnSuccess(v -> events.publishEvent(new RefreshRoutesEvent(this)))
                .thenReturn("redirect:/admin/ui/routes?deleted=" + enc(id))
                .onErrorResume(err -> Mono.just("redirect:/admin/ui/routes?error=" + enc(err.getMessage())));
    }

    @PostMapping("/routes/refresh")
    public Mono<String> refreshRoutes() {
        events.publishEvent(new RefreshRoutesEvent(this));
        return Mono.just("redirect:/admin/ui/routes");
    }

    // ────────────────────────────────────────────────────────────────
    // Audit
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/audit")
    public Mono<String> audit(Model model, @RequestParam(required = false) String routeId) {
        model.addAttribute("active", "audit");
        model.addAttribute("routeId", routeId);
        return (routeId != null && !routeId.isBlank()
                        ? auditRepo.findByRouteIdOrderByCreatedAtDesc(routeId).collectList()
                        : auditRepo.findRecent().collectList())
                .doOnNext(list -> model.addAttribute("entries", list))
                .thenReturn("audit/list");
    }

    // ────────────────────────────────────────────────────────────────
    // API Keys (create only — list requires a new store method)
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/apikeys")
    public Mono<String> apikeys(Model model,
                                @RequestParam(required = false) String rawKey,
                                @RequestParam(required = false) String keyId,
                                @RequestParam(required = false) String revoked,
                                @RequestParam(required = false) String error) {
        model.addAttribute("active", "apikeys");
        model.addAttribute("rawKey", rawKey);
        model.addAttribute("keyId", keyId);
        model.addAttribute("revoked", revoked);
        model.addAttribute("error", error);
        model.addAttribute("apiKeyEnabled",
                apiKeyStoreProvider.getIfAvailable() != null && apiKeyPropsProvider.getIfAvailable() != null);
        return Mono.just("apikeys/list");
    }

    @PostMapping("/apikeys")
    public Mono<String> createApiKey(@ModelAttribute ApiKeyForm form) {
        ApiKeyStore store = apiKeyStoreProvider.getIfAvailable();
        ApiKeyProperties props = apiKeyPropsProvider.getIfAvailable();
        if (store == null || props == null) {
            return Mono.just("redirect:/admin/ui/apikeys?error=" + enc("API keys disabled — set gateway.apikey.enabled=true"));
        }
        String rawKey = props.getKeyPrefix() + randomHex(32);
        String keyId = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        var record = new com.example.apigateway.apikey.ApiKeyRecord(
                keyId,
                rawKey.substring(0, Math.min(10, rawKey.length())),
                form.ownerId(),
                form.name(),
                form.scopesList(),
                form.tier() == null || form.tier().isBlank() ? "standard" : form.tier(),
                null,
                true,
                Instant.now());
        return store.save(record, rawKey)
                .thenReturn("redirect:/admin/ui/apikeys?rawKey=" + enc(rawKey) + "&keyId=" + enc(keyId))
                .onErrorResume(err -> Mono.just("redirect:/admin/ui/apikeys?error=" + enc(err.getMessage())));
    }

    @PostMapping("/apikeys/revoke")
    public Mono<String> revokeApiKey(@ModelAttribute RevokeForm form) {
        ApiKeyStore store = apiKeyStoreProvider.getIfAvailable();
        if (store == null) {
            return Mono.just("redirect:/admin/ui/apikeys?error=" + enc("API keys disabled"));
        }
        return store.revokeById(form.keyId())
                .map(deleted -> deleted
                        ? "redirect:/admin/ui/apikeys?revoked=" + enc(form.keyId())
                        : "redirect:/admin/ui/apikeys?error=" + enc("Key not found: " + form.keyId()))
                .onErrorResume(err -> Mono.just("redirect:/admin/ui/apikeys?error=" + enc(err.getMessage())));
    }

    // ────────────────────────────────────────────────────────────────
    // Form models
    // ────────────────────────────────────────────────────────────────

    /**
     * HTML form-backed. Predicates/filters submitted as raw JSON strings.
     * Setters required by Spring's WebFlux form binder.
     */
    public static class RouteForm {
        private String id;
        private String uri;
        private Integer order;
        private String predicatesJson;
        private String filtersJson;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public Integer getOrder() { return order; }
        public void setOrder(Integer order) { this.order = order; }
        public String getPredicatesJson() { return predicatesJson; }
        public void setPredicatesJson(String predicatesJson) { this.predicatesJson = predicatesJson; }
        public String getFiltersJson() { return filtersJson; }
        public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }

        public RouteDefinition toDefinition(ObjectMapper mapper) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
            if (uri == null || uri.isBlank()) throw new IllegalArgumentException("uri is required");
            List<PredicateDefinition> preds;
            List<FilterDefinition> filters;
            try {
                preds = mapper.readValue(predicatesJson == null ? "[]" : predicatesJson, PREDICATE_LIST);
                filters = filtersJson == null || filtersJson.isBlank()
                        ? List.of()
                        : mapper.readValue(filtersJson, FILTER_LIST);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON in predicates or filters: " + e.getMessage());
            }
            RouteDefinition def = new RouteDefinition();
            def.setId(id);
            def.setUri(URI.create(uri));
            def.setOrder(order == null ? 0 : order);
            def.setPredicates(preds);
            def.setFilters(filters);
            return def;
        }
    }

    public record ApiKeyForm(String ownerId, String name, String scopes, String tier) {
        public List<String> scopesList() {
            if (scopes == null || scopes.isBlank()) return List.of("read");
            return java.util.Arrays.stream(scopes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public record RevokeForm(String keyId) {}

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private static String enc(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.HexFormat.of().formatHex(buf);
    }

    // Reference to ApiKeyAdminController and RouteAdminController is only for
    // keeping them on the compile-time dependency graph — this controller
    // duplicates the CRUD logic against the same stores intentionally
    // (form-post semantics differ from REST JSON).
    @SuppressWarnings("unused")
    private void keepReferences(RouteAdminController r, ApiKeyAdminController k) {}
}
