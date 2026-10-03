# Tier 0 — Bug Fixes

Three real bugs in the pre-existing resource-server. Fixed first because
they could silently break the service in production or confuse anyone
reading the code.

---

## 1. The three bugs

```
┌─────────────────────────────────────────────────────────────────────────┐
│  #  Severity   Bug                                                       │
│  ─  ─────────  ────────────────────────────────────────────────────     │
│  1  Medium     Duplicate @RestController — unpredictable routing        │
│  2  Medium     Hardcoded JWKS URL — config drift risk                   │
│  3  Low        Missing explicit web starter — transitive-dep fragility  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Bug #1 — Duplicate @RestController

### What was wrong

`ResourceServerApplication` had an inner `@RestController` AND there was a
separate `ApiController` class — **both** mapped `/api/hello`.

```java
// ResourceServerApplication.java (BEFORE)
@SpringBootApplication
public class ResourceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
    }

    @RestController
    static class Api {
        @GetMapping("/api/hello")
        public String hello() {
            return "Hello from Resource Server!";
        }
    }
}

// ApiController.java (BEFORE)
@RestController
@RequestMapping("/api")
public class ApiController {
    @GetMapping("/hello")
    public String hello(Authentication authentication) {
        return "Hello, " + authentication.getName();
    }
}
```

### Why it's a bug

Spring sees two beans claiming the same URL path. In Spring Boot 3 this
usually throws `IllegalStateException: Ambiguous mapping` **at startup** —
but depending on bean registration order, one quietly wins. Which one? You
can't tell without reading the ApplicationContext dump.

```
                ┌──────────────────────────────┐
Browser ──▶     │  Spring MVC HandlerMapping   │
GET /api/hello  │                              │
                │  ??? one of two beans wins   │
                │  without any build-time or   │
                │  runtime warning in some     │
                │  Spring versions              │
                └──────────────────────────────┘
                         │
            which?       │       which?
            ┌────────────┴────────────┐
            ▼                         ▼
    "Hello from Resource     "Hello, " +
     Server!"                 authentication.getName()
    (no auth required?)      (requires auth)
```

### The fix

Delete the inner `Api` class. `ApiController` is the single source of truth
for `/api/*` endpoints.

```java
// ResourceServerApplication.java (AFTER)
@SpringBootApplication
public class ResourceServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
```

### Interview explanation

> Two `@RestController` beans mapped the same URL. Spring's behaviour is
> undefined in that case — some versions throw, some pick arbitrarily. I
> removed the duplicate so there's exactly one controller per path and the
> file layout matches the convention of one class per concern.

---

## 3. Bug #2 — Hardcoded JWKS URL

### What was wrong

`SecurityConfig` had an explicit `JwtDecoder` bean with a hardcoded JWKS URL.
Meanwhile `application.properties` ALSO declared the issuer-uri. Both are
sources of truth; neither knows about the other.

```java
// SecurityConfig.java (BEFORE)
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri("http://127.0.0.1:8095/oauth2/jwks").build();
}
```

```properties
# application.properties (BEFORE)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8095
```

### Why it's a bug

**Config drift risk.** When auth-server moves to a different host, you have
to update TWO places. Forget one and the service silently uses the stale URL.
There's no compile-time check.

```
       Dev:   application.properties → localhost:8095   ✓
              SecurityConfig.java    → 127.0.0.1:8095  (matches)

       Prod:  application.properties → auth.example.com:8443 ✓
              SecurityConfig.java    → 127.0.0.1:8095  ← STALE
                                                       → JWT decoder fetches
                                                         from wrong URL → every
                                                         request fails validation
```

Also notice the subtle mismatch: `localhost` vs `127.0.0.1`. These resolve
the same on most systems but differ on IPv6-only setups or when hosts files
map `localhost` to something unexpected.

### The fix

Delete the `@Bean JwtDecoder`. Spring Security auto-configures a decoder
from `spring.security.oauth2.resourceserver.jwt.issuer-uri` — one source of
truth, environment-overridable via standard mechanisms (profiles, env vars,
config server).

```java
// SecurityConfig.java (AFTER)
// JwtDecoder bean DELETED.
// issuer-uri in application.yml is the single source of truth.
```

```yaml
# application.yml (AFTER — switched from .properties to .yml too)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8095
```

### Interview explanation

> The JWKS URL was in two places — a `JwtDecoder` bean and the properties
> file. Changing one without the other would silently use the wrong value.
> Spring Security auto-configures the decoder from `issuer-uri`, so removing
> the manual bean makes the properties file the single source of truth and
> enables standard environment overrides.

---

## 4. Bug #3 — Missing explicit web starter

### What was wrong

`pom.xml` had no `spring-boot-starter-web` declaration. The service worked
because `spring-security-web` transitively brought it in.

```xml
<!-- pom.xml (BEFORE) -->
<dependencies>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-web</artifactId>
    </dependency>
    <!-- ... no spring-boot-starter-web ... -->
</dependencies>
```

### Why it's a bug

**Transitive deps are brittle.** When a library maintainer tightens
dependencies (removes a transitive, moves to `provided` scope, etc.), your
app silently stops starting. You get a cryptic error like
`NoClassDefFoundError: jakarta/servlet/Filter` months after upgrading.

```
Current state:
   spring-security-web  (transitive)─▶ spring-boot-starter-web
                                       │
                                       └─▶ spring-mvc, tomcat, etc.

One Spring Security refactor later:
   spring-security-web  ──X   (transitive removed)
                                       │
                                       └─▶ gone — resource-server won't start
```

### The fix

Add `spring-boot-starter-web` explicitly. Costs nothing (it's already there
transitively), but documents the intent and insulates against future
transitive-dep changes.

```xml
<!-- pom.xml (AFTER) -->
<dependencies>
    <!-- Spring MVC — explicit (previously came in transitively through
         spring-security-web; made explicit so a future refactor doesn't
         silently break startup). -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- ... security starter now, not raw modules ... -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>
```

Note we also swapped the raw security modules (`spring-security-config`,
`spring-security-web`, `spring-security-oauth2-jose`,
`spring-security-oauth2-resource-server`) for the SINGLE Spring Boot starter
`spring-boot-starter-oauth2-resource-server` which pulls them all in with
versions managed by the Spring Boot BOM. Less drift risk, less to maintain.

### Interview explanation

> The service worked but didn't explicitly declare the web starter — it
> relied on a transitive dependency from Spring Security. That's fragile:
> any Spring Security version bump could remove the transitive and the app
> would stop starting with a cryptic error. I made the dep explicit and
> consolidated the raw security modules into the Spring Boot starter.

---

## 5. Verification

```bash
cd /Users/bhanupratap/My/my-microservices
mvn -pl resource-server clean compile

# No "Ambiguous mapping" error
# No "ClassNotFoundException" at startup
mvn -pl resource-server spring-boot:run

# Request now goes through ApiController + hits auth gate
curl -i http://localhost:8096/api/hello
# → 401 Unauthorized (correct — ApiController requires auth)

# With a valid token
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8096/api/hello
# → "Hello, admin"
```

---

## 6. Interview cheat-sheet

| Question | Answer |
|---|---|
| How did you spot the duplicate controller? | Grep for `@RestController` in the module. Two hits, same path. In some Spring versions this would log an "Ambiguous mapping" warning at startup; in others it silently picks one. |
| Config drift — when does it bite? | Second time you touch the stale location: dev works, you deploy to prod with a new JWKS URL, forget to update SecurityConfig, every JWT request fails. 2am. |
| Why is `spring-boot-starter-X` better than the raw module? | Starter pulls a curated set with versions managed by the Boot BOM. Raw modules let individual version drift happen. Starter also brings auto-config classes that register beans. |
| Transitive deps — "don't rely on them, declare what you use." Is this always true? | Mostly yes. Transitive deps are correct-by-construction but fragile across refactors. For deps your code directly imports, declare explicitly. For deps your deps use internally, let them stay transitive. |
| Spring Security auto-config vs manual bean? | Auto-config reads properties, uses sensible defaults. Manual bean lets you override specific behaviour (custom validators, custom decoders). Use auto-config until you have a specific reason to override. |

---

## 7. Lessons

1. **Grep for duplicate URL mappings early.** `grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" src/main/java | sort -u -k2` surfaces them.
2. **One source of truth per config value.** If something's in a properties file, don't ALSO hardcode it in Java.
3. **Declare direct deps explicitly.** Transitive-only works until it doesn't.
4. **Prefer Spring Boot starters** over raw modules. Starters are the supported API; raw modules are implementation detail.
