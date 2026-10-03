# Thymeleaf on WebFlux — Deep Dive

Companion note to [admin-ui-thymeleaf.md](admin-ui-thymeleaf.md). That doc
covered the *what* (routes/audit/apikeys admin pages). This one covers the
*how* — the interview-payoff answers about running a servlet-designed
templating engine inside a reactive Netty pipeline.

No code changes here — this documents what `AdminUiThymeleafController` and
the templates in `src/main/resources/templates/` already do.

---

## 1. The core tension

```
Thymeleaf (original design, 2011):
  Called from servlet controllers, synchronously renders HTML to a Writer.
  Assumes: HttpServletRequest, HttpServletResponse, a Writer, blocking I/O.

Spring WebFlux (2017):
  Non-blocking, event-loop threads (Netty by default).
  Contract: return Publisher<T> (Mono/Flux). NEVER block on the request thread.

  Blocking Thymeleaf on a Netty thread = disaster:
    - Netty typically has 2 × CPU-count worker threads
    - Every blocking render pins one thread until done
    - N slow renders → all threads busy → gateway wedges
```

Thymeleaf's answer: a **reactive view resolver** that integrates with the
reactive rendering pipeline, resolves reactive Model attributes without
blocking, and streams output back through WebFlux's `ServerHttpResponse`.

---

## 2. Two integrations shipped in the same JAR

```
org.thymeleaf.spring6:
├── org.thymeleaf.spring6.view.ThymeleafViewResolver          ← servlet MVC
│   └─ Uses SpringTemplateEngine (synchronous)
│
└── org.thymeleaf.spring6.view.reactive.ThymeleafReactiveViewResolver  ← WebFlux
    └─ Uses SpringWebFluxTemplateEngine (reactive-aware)
```

Spring Boot's `ThymeleafAutoConfiguration` decides which to wire based on the
web application type:

```
@ConditionalOnWebApplication(type = SERVLET)  → wires ThymeleafViewResolver
@ConditionalOnWebApplication(type = REACTIVE) → wires ThymeleafReactiveViewResolver
```

Your gateway has `spring-cloud-starter-gateway` → brings
`spring-boot-starter-webflux` → adding `spring-boot-starter-thymeleaf`
auto-wires the reactive resolver. Zero config.

**Interview trap** — accidentally add `spring-boot-starter-web` (servlet) →
Spring Boot picks servlet auto-config → gateway breaks weirdly because Cloud
Gateway requires WebFlux. Never add `-web` to a WebFlux app.

---

## 3. What the reactive resolver actually does

```
    ┌──────────────────────────────────────────────────────────────────────┐
    │  Controller returns Mono<String>("routes/list") + Model              │
    │                                                                      │
    │      routeRepo.getRouteDefinitions()                                 │
    │            .collectList()                                            │
    │            .doOnNext(list -> model.addAttribute("routes", list))     │
    │            .thenReturn("routes/list")                                │
    └──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │  ViewResolutionResultHandler (WebFlux)                               │
    │  Resolves view name → View instance via ThymeleafReactiveViewResolver│
    └──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │  ThymeleafReactiveView.render(model, contentType, exchange)          │
    │    returns Mono<Void>                                                │
    │                                                                      │
    │  1. Walk Model — detect Publisher<T> values                          │
    │  2. Subscribe them in PARALLEL                                       │
    │  3. When all resolved, invoke engine.process(template, context)      │
    │  4. Deliver rendered bytes via configured mode                       │
    │      (FULL / CHUNKED / DATA-DRIVEN — see next section)               │
    └──────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │  ServerHttpResponse.writeWith(Flux<DataBuffer>)                      │
    │  Netty writes to socket                                              │
    └──────────────────────────────────────────────────────────────────────┘
```

The template engine itself is largely the same code as servlet Thymeleaf. The
DIFFERENCE is *where* it runs and *how* output is delivered.

---

## 4. FULL vs CHUNKED vs DATA-DRIVEN

```
┌──────────────────────────────────────────────────────────────────────────┐
│  FULL mode (default)                                                     │
│  ────────                                                                │
│  1. Model fully resolved (any Publisher values awaited)                  │
│  2. Template engine renders complete HTML into a buffer                  │
│  3. Buffer written to response as one DataBuffer                         │
│                                                                          │
│  Use case: normal HTML pages. Predictable, simple, universal.            │
│  Cost: full page held in memory during render. Fine for admin pages.     │
│                                                                          │
│  CHUNKED mode                                                            │
│  ────────                                                                │
│  Enable via: spring.thymeleaf.reactive.max-chunk-size: 4096              │
│                                                                          │
│  Engine writes output progressively in chunks of N bytes.                │
│  Response streams to client as chunks arrive.                            │
│                                                                          │
│  Use case: large pages where first-byte latency matters (dashboards).    │
│                                                                          │
│  DATA-DRIVEN mode                                                        │
│  ──────────                                                              │
│  Enable via: model attribute + view name in                              │
│              spring.thymeleaf.reactive.chunked-mode-view-names           │
│                                                                          │
│  Template iterates a reactive stream (Flux) — engine renders one         │
│  chunk per emission and back-pressures the Flux.                         │
│                                                                          │
│  Use case: massive tables, SSE-like real-time pages, log tails.          │
└──────────────────────────────────────────────────────────────────────────┘
```

Our admin UI uses **FULL mode** — small pages, simple. Nothing streams.

---

## 5. Controller signatures — what compiles and what doesn't

### `Mono<String>` (canonical)

```java
@GetMapping("/routes")
public Mono<String> routes(Model model) {
    return routeRepo.getRouteDefinitions()
            .collectList()
            .doOnNext(list -> model.addAttribute("routes", list))
            .thenReturn("routes/list");
}
```

Framework: sees `Mono<String>`, resolves view name once Mono emits. Data
lookup happens reactively, no blocking.

### Plain `String` (also works)

```java
@GetMapping("/audit")
public String audit(Model model) {
    model.addAttribute("entries", List.of());  // synchronous
    return "audit/list";
}
```

Framework: wraps the String in `Mono.just(...)` internally. Fine for
synchronous data. If you touch reactive repos inside without collecting
properly, you'll get race conditions — use `Mono<String>`.

### Async model attributes (magic)

```java
@GetMapping("/routes")
public String routes(Model model) {
    Mono<List<RouteDefinition>> routes = routeRepo.getRouteDefinitions().collectList();
    model.addAttribute("routes", routes);   // ← a Mono, not a List
    return "routes/list";
}
```

The reactive view resolver **detects Publisher values in the Model** and
subscribes to them during render. When they emit, the template renders with
resolved values. **No manual `.block()`.** Multiple Publishers subscribe in
parallel — page with 3 slow queries renders in the time of the slowest, not
the sum.

### `subscribe()` without waiting (broken)

```java
@GetMapping("/routes")
public String routes(Model model) {
    routeRepo.getRouteDefinitions().collectList()
             .subscribe(list -> model.addAttribute("routes", list));  // fire-and-forget!
    return "routes/list";
}
```

`subscribe()` returns immediately; the Model may not be set by render time.
Race condition. **Never do this.**

### `.block()` in the controller (broken)

```java
@GetMapping("/routes")
public String routes(Model model) {
    List<RouteDefinition> list = routeRepo.getRouteDefinitions().collectList().block();  // BLOCKED
    model.addAttribute("routes", list);
    return "routes/list";
}
```

Reactor throws `IllegalStateException: block()/blockFirst()/blockLast() are
blocking, which is not supported in thread reactor-http-nio-...`. Netty
threads are protected.

---

## 6. The Model — synchronous or reactive?

`org.springframework.ui.Model` is the same interface as MVC. Values can be:

```
Sync:    model.addAttribute("routes", List.of(r1, r2, r3))
Mono:    model.addAttribute("routes", Mono.just(List.of(...)))     ← resolved during render
Flux:    model.addAttribute("entries", auditRepo.findRecent())     ← resolved during render
```

Template usage is identical: `th:each="r : ${routes}"`. Thymeleaf doesn't
know if `routes` came from a Mono — it iterates the resolved value.

Concurrency gain: **3 async attributes subscribe in parallel**. Small page
with 3 DB lookups renders as fast as the slowest one — not sum-of-all.

---

## 7. Form binding — the WebFlux difference

`@ModelAttribute` works the same as MVC at the source level, but:

```java
// Plain POJO with setters — always works
public static class RouteForm {
    private String id;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    // ...
}

// Records — Spring 6.1+ supports for MVC, WebFlux is finicky in older versions
public record RouteForm(String id, String uri) {}
```

Records via `@ModelAttribute` on WebFlux is technically supported (Spring
6.1+) but I've hit issues in some Spring Cloud Gateway versions and default
to the POJO in `AdminUiThymeleafController` for the routes form. Interview
note: *"records + @ModelAttribute + WebFlux = verify with your actual
version; POJO with setters is always safe."*

---

## 8. Fragments — identical to servlet Thymeleaf

```html
<!-- layout.html -->
<head th:fragment="head(title)">
    <title th:text="${title}"></title>
    <link ...>
</head>

<header th:fragment="topbar(active)">
    <nav>
        <a th:href="@{/admin/ui/routes}"
           th:classappend="${active == 'routes' ? 'tab active' : 'tab'}">Routes</a>
        ...
    </nav>
</header>

<!-- routes/list.html -->
<head th:replace="~{layout :: head('Routes — Admin')}"></head>
<body>
<div th:replace="~{layout :: topbar('routes')}"></div>
...
```

Fragments are compile-time includes — no reactive vs blocking difference.

---

## 9. When the reactive integration bites you

### 9a. Blocking libraries inside Thymeleaf expressions

```html
<span th:text="${someService.expensiveDbCall()}"></span>
```

If `expensiveDbCall()` is BLOCKING (JDBC, etc.), it runs on the render thread.
In reactive mode that's usually a background executor — but if misconfigured,
could still be a Netty thread.

**Fix**: pre-compute in the controller, put the result in the Model.

### 9b. Session / request scoping

MVC Thymeleaf idioms sometimes depend on servlet scopes (`session`, `request`
attributes). WebFlux doesn't have those. Use `WebSession`:

```java
public Mono<String> page(Model model, WebSession session) {
    return Mono.justOrEmpty(session.<String>getAttribute("theme"))
            .defaultIfEmpty("light")
            .doOnNext(theme -> model.addAttribute("theme", theme))
            .thenReturn("page");
}
```

### 9c. Error handling

MVC: throw an exception, `@ControllerAdvice` catches, renders error page.

WebFlux Thymeleaf: same annotation-based flow works. Async errors inside a
returned `Mono` propagate through Reactor's error signal — handle with a
global `@ExceptionHandler` or `.onErrorResume(...)` in the Mono chain.

`AdminUiThymeleafController` uses per-call `.onErrorResume(err -> Mono.just("redirect:..."))`
because failures are per-form-action, not systemic.

---

## 10. Config knobs (application.yml)

```yaml
spring:
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    mode: HTML                        # default
    encoding: UTF-8
    cache: true                       # false in dev — reloads templates on every request
    check-template: true
    check-template-location: true

    reactive:
      # Empty list = all views use FULL mode. Add specific view names to enable CHUNKED.
      chunked-mode-view-names: []

      # 0 = write full response in one chunk. > 0 = flush chunks of N bytes.
      max-chunk-size: 0

      # For data-driven mode: view names that stream from a reactive data source.
      full-mode-view-names: []

      # Media types Thymeleaf produces (default: text/html)
      media-types:
        - text/html
```

For our admin UI: defaults are correct. Set `cache: false` in dev so template
edits reload without restart:

```yaml
spring.thymeleaf.cache: false      # dev only
```

---

## 11. Interview cheat-sheet

| Question | Answer |
|---|---|
| Can Thymeleaf run on WebFlux? | Yes — `spring-boot-starter-thymeleaf` auto-detects WebFlux and wires `ThymeleafReactiveViewResolver` + `SpringWebFluxTemplateEngine`. No servlet dep needed. |
| Controller return type on WebFlux? | `Mono<String>` (canonical). Plain `String` also works — framework wraps it. Never `.block()`. |
| Async data in Model? | Yes — `model.addAttribute("x", Mono.just(...))`. Reactive resolver subscribes during render. Multiple async attrs subscribe in parallel. |
| FULL vs CHUNKED vs DATA-DRIVEN? | FULL (default) buffers whole page. CHUNKED flushes N-byte chunks (lower TTFB). DATA-DRIVEN streams from a Flux — for tables / SSE-like pages. |
| Blocking library in a template expression? | Runs on the render thread. If blocking JDBC, risks blocking Netty. Pre-compute in the controller. |
| Session on WebFlux? | Use `WebSession` (reactive). MVC's `HttpSession` doesn't exist. |
| Records for form binding? | Spring 6.1+ supports for MVC; WebFlux support finicky in older versions. POJOs with setters always safe. |
| Fragments? | Same syntax as MVC (`th:fragment`, `th:replace`). No difference. |
| Error handling? | `@ExceptionHandler` still works. Async errors in returned Mono → `.onErrorResume(...)` in the chain. |
| How does the resolver subscribe Model Monos? | `AbstractThymeleafReactiveView.render()` walks the Model, detects `Publisher` types, resolves them before rendering. |
| Concurrent subscription of Model attrs? | Yes — parallel by default. |
| Overhead vs MVC? | Slightly higher per-request setup. Vastly more throughput under load. Only matters at scale. |
| When would you NOT use Thymeleaf on WebFlux? | If the team knows MVC + servlet cold, don't switch just for Thymeleaf. Use it when you already need WebFlux (Cloud Gateway, high-throughput API). |

---

## 12. Common pitfalls (interview probes)

1. **Two starters at once** — `spring-boot-starter-web` (servlet) + `spring-boot-starter-webflux` (reactive) → Spring Boot picks servlet, WebFlux features break. Cloud Gateway needs WebFlux; NEVER add `-web`.
2. **`.block()` in a controller returning `Mono`** — Reactor throws `IllegalStateException` on Netty threads. Detected fast, painful to debug in a lambda.
3. **`subscribe()` without waiting** — fire-and-forget in a controller = race condition. Template renders before data arrives. Model attribute is null.
4. **Assuming Model attrs render in order** — with multiple async attrs, they render when their Mono emits. Order in your controller code ≠ order of resolution.
5. **`session.` in a template** — WebFlux doesn't populate a `session` implicit variable. Use `#session` after populating via `WebSession`.
6. **FULL mode + huge dataset** — buffers everything before flushing. Renders 100MB pages fine, but uses 100MB of heap. Switch to CHUNKED.
7. **Caching stale templates in dev** — `spring.thymeleaf.cache: true` is default. Set `false` in dev so edits reload without restart.
8. **Records + @ModelAttribute** — flaky before Spring 6.1. Use POJO with setters if unsure.

---

## 13. Where this lives in your code

- **Controller**: `AdminUiThymeleafController.java` (~250 lines). `Mono<String>` returns, `Model.addAttribute` for sync data, `.doOnNext` + `.thenReturn` for reactive pipelines, `.onErrorResume` for per-action error handling.
- **Templates**: `src/main/resources/templates/**/*.html`. Standard Thymeleaf syntax with `th:each`, `th:if`, `th:href`, `th:fragment`. Zero reactive-specific syntax.
- **Auto-wired resolver**: `ThymeleafReactiveViewResolver` bean created by Spring Boot's `ThymeleafAutoConfiguration.ReactiveConfiguration`.

Inspect the wired beans at runtime:

```bash
curl -s -u admin:admin123 http://localhost:8080/actuator/beans \
  | jq '.contexts.application.beans | to_entries[] | .key' | grep -i thymeleaf
# "thymeleafReactiveViewResolver"
# "thymeleafViewResolver"     (may or may not exist depending on classpath)
# "thymeleafReactive"          (the SpringWebFluxTemplateEngine)
# "templateEngine"             (the underlying Thymeleaf engine)
```

---

## 14. Extensions (parked)

- **DATA-DRIVEN mode** — stream a Flux of audit entries into the template. Useful for long log tables where first-byte latency matters.
- **SSE + Thymeleaf** — render an SSE endpoint returning Thymeleaf-rendered HTML fragments per event. Combined with HTMX on the client for real-time updates.
- **HTMX + fragment endpoints** — reduce full-page reloads by returning fragments (`th:fragment` blocks) directly to HTMX AJAX calls.
- **Custom `IExpressionObjectFactory`** — add domain helpers to Thymeleaf's expression scope (`${#myUtils.formatFoo(...)}`).
- **Fragment caching** — `spring.thymeleaf.cache: true` + `TemplateEngine.setCacheable(fragment)` for expensive-to-render partials.
- **i18n with WebFlux MessageSource** — `#{message.key}` works; resolution respects the request's locale via WebFlux `LocaleContextResolver`.

---

## 15. TL;DR

- Thymeleaf ships a reactive view resolver alongside its servlet one
- Spring Boot picks the right resolver automatically based on WebFlux vs servlet
- Controllers return `Mono<String>` (or plain `String` for sync data)
- Model attributes can be `Publisher<T>` — resolver subscribes during render
- Fragments and expression syntax are identical to servlet Thymeleaf
- Traps: don't mix servlet + WebFlux starters; don't call `.block()`; don't `subscribe()` without waiting
