# Spring Security — Study Notes

Study guide for interview + concept clarity. Sequenced from big-picture → mechanics → advanced customization.

---

## Table of contents

1. [The one key idea](#1-the-one-key-idea)
2. [Request flow — where Spring Security fits](#2-request-flow--where-spring-security-fits)
3. [Authentication vs Authorization](#3-authentication-vs-authorization)
4. [The filter-chain architecture](#4-the-filter-chain-architecture)
5. [The 5 core objects you configure](#5-the-5-core-objects-you-configure)
6. [The 3 customization zones](#6-the-3-customization-zones)
7. [Default configuration (what you get for free)](#7-default-configuration-what-you-get-for-free)
8. [How defaults are replaced (auto-config mechanism)](#8-how-defaults-are-replaced-auto-config-mechanism)
9. [Writing a custom `SecurityConfig`](#9-writing-a-custom-securityconfig)
10. [What `.loginPage(...)` actually changes](#10-what-loginpage-actually-changes)
11. [Login flow — POST /login is claimed by a filter](#11-login-flow--post-login-is-claimed-by-a-filter)
12. [Authentication sub-flow (deeper)](#12-authentication-sub-flow-deeper)
13. [Authorization sub-flow — `AccessDecisionManager` / voters](#13-authorization-sub-flow--accessdecisionmanager--voters)
14. [After-login redirect — `RequestCache` + `SavedRequest`](#14-after-login-redirect--requestcache--savedrequest)
15. [Session-based vs stateless](#15-session-based-vs-stateless)
16. [SecurityContext lifecycle across requests](#16-securitycontext-lifecycle-across-requests)
17. [CSRF protection](#17-csrf-protection)
18. [AuthenticationEntryPoint](#18-authenticationentrypoint)
19. [Accessing the authenticated user in code](#19-accessing-the-authenticated-user-in-code)
20. [Handlers vs Events vs Filters vs Controllers](#20-handlers-vs-events-vs-filters-vs-controllers)
21. [Interview Q&A](#21-interview-qa)
22. [Recommended learning path](#22-recommended-learning-path)

---

## 1. The one key idea

**Spring Security is not inside your controller.** It is a layer that intercepts every HTTP request **before** it reaches your controller. Its job is to decide:

- **Who is the user?** — Authentication
- **Is the user allowed to access this resource?** — Authorization

Only after both questions are answered successfully does your application execute its business logic.

---

## 2. Request flow — where Spring Security fits

```
        Internet
           │
           ▼
        Tomcat
           │
           ▼
   Spring Security      ← Security starts here
           │
   Is user authenticated?
     ┌─────┴─────┐
     │           │
     No          Yes
     │           │
     ▼           ▼
   401/403    DispatcherServlet
              │
              ▼
          Controller
              │
              ▼
           Service
              │
              ▼
          Repository
              │
              ▼
           Database
```

**Depending on your configuration, Spring Security can check:**

- Username and password
- JWT access token
- User roles (`ADMIN`, `USER`)
- Authorities (permissions)
- Session
- CSRF token (for browser-based apps)
- CORS rules

All these checks happen **before** your business logic runs.

---

## 3. Authentication vs Authorization

Two words that sound similar but do different things.

| | Authentication | Authorization |
|---|---|---|
| **Question** | Who are you? | Are you allowed? |
| **Example** | Verifying password | Checking role / permission |
| **Happens** | Once, at login | On every protected request |

Ex: 

| Airport           | Spring Security         |
| ----------------- | ----------------------- |
| Airport Manager   | **FilterChainProxy**    |
| Security Lane     | **SecurityFilterChain** |
| Security Officers | **VirtualFilterChain**  |


You almost always do **authentication first**, then **authorization** on each request.

---

## 4. The filter-chain architecture

Spring Security is essentially a **stack of filters** that runs inside Tomcat's filter pipeline.

### The 3 top-level components

| Component | Responsibility |
|---|---|
| **DelegatingFilterProxy** | Bridge between Tomcat and Spring. Delegates the request to Spring Security. |
| **FilterChainProxy** | Central coordinator. Selects the appropriate `SecurityFilterChain` and executes it. |
| **SecurityFilterChain** | Contains the ordered list of security filters and the authorization rules for matching requests. |

### The full pipeline

```
Browser
   │
   ▼
Tomcat
   │
   ▼
┌────────────────────────────────────┐
│ DelegatingFilterProxy              │
│ "Forward to Spring Security"       │
└────────────────────────────────────┘
   │
   ▼
┌────────────────────────────────────┐
│ FilterChainProxy                   │
│ "Which SecurityFilterChain?"       │
└────────────────────────────────────┘
   │
   ▼
┌────────────────────────────────────┐
│ SecurityFilterChain                │
│ Collection of security rules       │
└────────────────────────────────────┘
   │
   ▼
DisableEncodeUrlFilter
   │
   ▼
SecurityContextHolderFilter
   │
   ▼
HeaderWriterFilter
   │
   ▼
CsrfFilter
   │
   ▼
LogoutFilter
   │
   ▼
UsernamePasswordAuthenticationFilter
   │
   ▼
AnonymousAuthenticationFilter
   │
   ▼
AuthorizationFilter
   │
   ▼
DispatcherServlet
   │
   ▼
Controller → Service → Repository → Database
```

Each filter does one job. `CsrfFilter` blocks bad POSTs. `LogoutFilter` intercepts `/logout`. `UsernamePasswordAuthenticationFilter` intercepts `POST /login`. `AuthorizationFilter` enforces URL rules. And so on.

---

## 5. The 5 core objects you configure

Spring Security has hundreds of classes, but only 5 you'll touch at first.

### 5.1 `SecurityFilterChain` — the config bean

One `@Bean` method where you declare public URLs, protected URLs, and which login mechanism to use.

```java
@Bean
SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(a -> a
            .requestMatchers("/auth/login", "/css/**", "/js/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(f -> f
            .loginPage("/auth/login")
            .defaultSuccessUrl("/dashboard"));
    return http.build();
}
```

### 5.2 `UserDetailsService` — how to look up users

An interface with one method: `loadUserByUsername(name) → UserDetails`. Spring calls it during login.

- **Learning**: `InMemoryUserDetailsManager` (users hardcoded in Java)
- **Real**: You implement it — usually calls a JPA repository

### 5.3 `PasswordEncoder` — never store plaintext

Hashes and verifies passwords. Default is `BCryptPasswordEncoder`.

```java
encoder.encode("demo123");        // → "$2a$10$Xf..." (store this)
encoder.matches("demo123", hash); // → true (login-time check)
```

### 5.4 `AuthenticationManager` — orchestrates the check

Given `(username, password)`, calls `UserDetailsService`, uses `PasswordEncoder` to compare, returns success/failure. You rarely touch it directly — Spring auto-wires it from your `UserDetailsService` + `PasswordEncoder` beans.

### 5.5 `SecurityContextHolder` — "who is logged in right now?"

Thread-local container. After login, Spring puts an `Authentication` object in it. Read it from any controller:

```java
@GetMapping("/welcome")
String welcome(Principal principal, Model model) {
    model.addAttribute("user", principal.getName()); // no more session.getAttribute()
    return "welcome";
}
```

---

## 6. The 3 customization zones

When you customize Spring Security, you're really customizing one of three zones:

| Zone | Core interfaces / classes | Purpose |
|---|---|---|
| **1. Authentication** | `UserDetailsService`, `AuthenticationProvider`, `AuthenticationManager` | Verify user credentials and build the `Authentication` object |
| **2. Authorization** | `AccessDecisionVoter`, `AccessDecisionManager`, HTTP security rules, `@PreAuthorize` | Decide what an authenticated user can access |
| **3. Protection / Filters** | `SecurityFilterChain`, custom `OncePerRequestFilter` | Add custom security logic (tokens, audit, rate-limit, headers) |

Any Spring Security customization you'll ever do sits in one of these three buckets.

---

## 7. Default configuration (what you get for free)

When you have zero `SecurityConfig`, Spring Security auto-configures:

1. **Every URL requires authentication** — `anyRequest().authenticated()`
2. **One in-memory user** — username `user`, random UUID password printed at startup
3. **Form login enabled** — Spring serves its own generated `/login` HTML page
4. **HTTP Basic auth enabled** — for API clients (curl, Postman)
5. **CSRF protection enabled** — POSTs need a token
6. **Session-based storage** — a `JSESSIONID` cookie holds the auth
7. **Default logout** at `POST /logout` (also serves `GET /logout` if CSRF is off)
8. **A default `AuthenticationEntryPoint`** — redirects unauthenticated browser requests to `/login`

That's Spring's opinion of "reasonable defaults." Designed so a fresh app is at least safe out of the box — not so you use it in production.

---

## 8. How defaults are replaced (auto-config mechanism)

The auto-config lives in a class called `SecurityAutoConfiguration` (from `spring-boot-autoconfigure`). It runs **only when there is no user-defined `SecurityFilterChain` bean in the context**.

The trigger is a Spring annotation on the auto-config class:

```java
@ConditionalOnMissingBean(SecurityFilterChain.class)
```

Read it literally: *"apply my configuration ONLY if the user did not define their own `SecurityFilterChain`."*

The moment you write:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        // your rules
    }
}
```

That `@Bean` exists → `@ConditionalOnMissingBean` fails → Spring's default filter chain is **not** created → your rules take over completely.

### There are actually TWO independent auto-configs

| Auto-config | Fires when… | What it provides |
|---|---|---|
| `SecurityAutoConfiguration` | No `SecurityFilterChain` bean exists | The default filter chain (form login on `/login`, etc.) |
| `UserDetailsServiceAutoConfiguration` | No `UserDetailsService` / `AuthenticationManager` / `AuthenticationProvider` bean exists | An in-memory user `user` with a random password logged at startup |

That's why the "Using generated security password" line keeps appearing until you provide **your own** `UserDetailsService` — even if you've written a `SecurityFilterChain`.

---

## 9. Writing a custom `SecurityConfig`

### The critical misconception to unlearn

- ❌ "SecurityConfig **adds** my rules on top of Spring's defaults."
- ✅ "SecurityConfig **replaces** the default `SecurityFilterChain` entirely. Anything you don't configure isn't there."

That's why, the first time people write a `SecurityConfig` with just:

```java
http.authorizeHttpRequests(a -> a.anyRequest().authenticated());
return http.build();
```

...they lose form login, lose CSRF exemptions, lose the login page — everything. They're not adding to defaults; they're starting from a smaller baseline and building back up.

*(Technically Spring gives you a small pre-populated `HttpSecurity` — CSRF is on, session management is set up — but the URL rules, login page, and logout behavior are yours to declare.)*

### Minimal working config

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
```

This is functionally very close to the defaults — the point of writing it is to prove *you* own the chain now.

---

## 10. What `.loginPage(...)` actually changes

That one method call flips **three things** inside Spring's `FormLoginConfigurer`:

1. **`AuthenticationEntryPoint`** — the "where do I send unauthenticated users?" handler switches from `/login` to the URL you pass.
2. **`loginProcessingUrl`** — the URL that receives the POSTed form. By default it's the SAME as the login page URL. So `POST /auth/login` is now what Spring's `UsernamePasswordAuthenticationFilter` intercepts.
3. **Auto-generated login page** — Spring's built-in HTML at `/login` gets turned OFF. Spring assumes you're serving your own HTML at the URL you named.

### Common gotcha — you MUST permit the login page URL

Otherwise you get an infinite redirect loop:

```
GET /auth/login → not authenticated → redirect to /auth/login → not authenticated → ...
```

Fix:

```java
.requestMatchers("/auth/login").permitAll()
```

---

## 11. Login flow — POST /login is claimed by a filter

### Before Spring Security

```
POST /miniapp/login
       ↓
Your @Controller.doLogin()
       ↓
You manually check credentials + put user in session
```

Your controller method was the entry point.

### After Spring Security

```
POST /miniapp/login
       ↓
[ Filter chain ]
       ↓
UsernamePasswordAuthenticationFilter   ← intercepted HERE, before controller
       ↓
    - Reads username + password from form
    - Calls UserDetailsService.loadUserByUsername("alex")
    - Uses PasswordEncoder.matches(...)
    - On success: stores Authentication in SecurityContext, redirects to /welcome
    - On failure: redirects to /login?error
       ↓
Your @Controller.doLogin()   ← NEVER CALLED
```

**Key point**: The request never reaches any controller for POST /login. That's why any manual `doLogin()` method in your controller becomes dead code the moment `.loginPage(...)` is set.

---

## 12. Authentication sub-flow (deeper)

Inside `UsernamePasswordAuthenticationFilter`, the credential check goes through several layers:

```
UsernamePasswordAuthenticationFilter
              │
              ▼
     AuthenticationManager
              │
              ▼
┌────────────────────────────────────────┐
│   One or more AuthenticationProvider   │
│   (DAO, LDAP, JWT, OAuth2, custom...)  │
└────────────────────────────────────────┘
              │
              ▼
UserDetailsService → loads user from DB
              │
              ▼
PasswordEncoder → verifies password
              │
              ▼
Authentication (principal + roles)
stored in SecurityContextHolder
```

### Roles of each object

- **`AuthenticationManager`** — entry point. Delegates to providers.
- **`AuthenticationProvider`** — knows how to check ONE type of credential (username/password, JWT, LDAP, etc.). Multiple providers can be chained.
- **`UserDetailsService`** — loads the user record (from memory, DB, LDAP).
- **`PasswordEncoder`** — hashes the submitted password and compares against the stored hash.
- **`Authentication`** — the result. Contains principal + authorities. Stored in `SecurityContextHolder`.

### Outcomes

- **Success** — fully authenticated `Authentication` object stored in `SecurityContextHolder` and in the HTTP session.
- **Failure** — throws exception → `ExceptionTranslationFilter` catches it → redirects to `/login?error`.

---

## 13. Authorization sub-flow — `AccessDecisionManager` / voters

Once authenticated, every request runs through the authorization sub-flow: "can this user access THIS URL/method?"

```
AuthorizationFilter (or FilterSecurityInterceptor in older versions)
              │
              ▼
    AccessDecisionManager
              │
              ▼
    AccessDecisionVoters (role checks, expressions)
              │
    ┌─────────┴─────────┐
    │                   │
   allow                deny
    │                   │
    ▼                   ▼
Controller runs   403 Forbidden (or redirect)
```

### Common voters

- **`WebExpressionVoter`** — evaluates SpEL expressions like `hasRole('ADMIN')`
- **`RoleVoter`** — matches role names directly
- **`AuthenticatedVoter`** — checks the authentication level (anonymous / remember-me / full)

### `AccessDecisionManager` strategies

- **`AffirmativeBased`** (default) — allow if ANY voter says yes
- **`ConsensusBased`** — majority wins
- **`UnanimousBased`** — all voters must agree

Most apps never touch this — the defaults work fine. Interview-worthy to know it exists.

---

## 14. After-login redirect — `RequestCache` + `SavedRequest`

### Two scenarios

**Scenario A — user was redirected TO login from a protected page:**

1. User visits `/dashboard` (protected)
2. Not authenticated → Spring saves `/dashboard` in the session
3. Redirects to `/auth/login`
4. User logs in successfully
5. Spring reads the saved URL → redirects to `/dashboard` ✅

**Scenario B — user visited `/auth/login` directly:**

1. No saved URL exists
2. After login, Spring redirects to `/` (the default fallback)

### The mechanism (interview-worthy)

- When an unauthenticated user hits a protected URL, `ExceptionTranslationFilter` catches the "access denied" and asks a bean called `RequestCache` to **save the original request**.
- Default implementation: `HttpSessionRequestCache` — stores it in the `HttpSession`.
- After login, `SavedRequestAwareAuthenticationSuccessHandler` checks the cache. If a saved request exists, redirect there. If not, redirect to the fallback URL.

### Flow diagram

```
GET /dashboard  → ExceptionTranslationFilter catches "not authenticated"
                → RequestCache.saveRequest(...) puts URL in session
                → EntryPoint redirects to /auth/login
                │
                ▼
POST /auth/login  → UsernamePasswordAuthenticationFilter validates
                  → Success
                  → SavedRequestAwareAuthenticationSuccessHandler runs
                  → Reads saved request from cache
                  → Redirect to /dashboard
```

### Three ways to configure post-login destination

```java
// (1) Fallback only — go here IF no saved request exists
.defaultSuccessUrl("/dashboard")

// (2) Always — ignore saved request, always go here
.defaultSuccessUrl("/dashboard", true)

// (3) Full custom logic — role-based routing, etc.
.successHandler((req, res, auth) -> {
    boolean admin = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    res.sendRedirect(admin ? "/admin" : "/dashboard");
})
```

---

## 15. Session-based vs stateless

### Session-based

- After login, Spring stores auth in `HttpSession`
- Browser gets a `JSESSIONID` cookie
- Every subsequent request loads the auth from the session
- Same model as manual session code, but Spring manages it

### Stateless (typical for REST APIs with JWT)

- No session, no cookie
- Every request carries a token in `Authorization: Bearer ...`
- Server validates the token per-request

You can have both in the same app — `SecurityFilterChain` supports multiple chains matched by URL.

### Important: session key is `SPRING_SECURITY_CONTEXT`

`session.getAttribute("user")` returns **null** — you never set that key. Spring stores auth under the key `SPRING_SECURITY_CONTEXT`. Always read via `Principal` / `Authentication`, never directly from the session.

---

## 16. SecurityContext lifecycle across requests

Because HTTP is stateless, the `SecurityContext` needs to be **loaded** at the start of each request and **saved** at the end — otherwise the user would need to re-authenticate on every request.

```
[ Request start ]
        ↓
SecurityContextHolderFilter loads security context from session
        ↓
Filters + controller use SecurityContextHolder.getContext()
        ↓
SecurityContextHolderFilter saves context back to session
        ↓
[ Response sent ]
```

### Where the context lives

- **During the request** — thread-local (`SecurityContextHolder`)
- **Between requests** — HTTP session, under key `SPRING_SECURITY_CONTEXT`

### Version notes

- **Older Spring Security (< 6)** — done by `SecurityContextPersistenceFilter`
- **Spring Security 6+** — done by `SecurityContextHolderFilter` (load happens automatically at the start; save happens on-demand)

### For stateless (JWT) auth

There is no persistence — the context is populated from the incoming token on every request and discarded after.

---

## 17. CSRF protection

**CSRF (Cross-Site Request Forgery)**: a malicious site tricks your logged-in browser into making a state-changing request to your app.

Spring Security's `CsrfFilter` blocks any POST/PUT/DELETE that doesn't include a valid CSRF token. A logout form will get **403 Forbidden** unless the token is included.

### Two options

**Keep CSRF on (recommended for browser-facing apps):**

```html
<form th:action="@{/logout}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit">Sign out</button>
</form>
```

Thymeleaf's Spring Security dialect auto-injects `_csrf` when the dependency is on the classpath.

**Turn CSRF off:**

```java
.csrf(csrf -> csrf.disable())
```

Only OK for pure REST APIs authenticated by tokens (JWT). **Never** for browser-facing forms.

---

## 18. AuthenticationEntryPoint

If a tutorial mentions "entry point", they usually mean `AuthenticationEntryPoint` — a specific Spring Security interface.

**Its job:** what happens when an unauthenticated user tries to hit a protected page?

- For form login: default entry point **redirects to `/login`**
- For REST APIs: you configure it to **return HTTP 401** instead

### Two things called "entry point" (do not confuse them)

1. **Where the request first lands in code** — that's the filter (like `UsernamePasswordAuthenticationFilter`)
2. **`AuthenticationEntryPoint` interface** — the "what do I do when someone isn't logged in?" hook

---

## 19. Accessing the authenticated user in code

| Method | Use when | Recommended |
|---|---|:---:|
| `Authentication` | Controller needs full authentication details | ⭐⭐⭐⭐⭐ |
| `Principal` | You only need the username | ⭐⭐⭐⭐ |
| `@AuthenticationPrincipal` | You need your custom `UserDetails` object | ⭐⭐⭐⭐⭐ |
| `SecurityContextHolder` | Service, utility, scheduled job, or code without request parameters | ⭐⭐⭐ |
| Thymeleaf `sec:authentication` | Display user info directly in UI | ⭐⭐⭐⭐⭐ |

### Examples

```java
// Simplest — just need the username
@GetMapping("/welcome")
String welcome(Principal principal) {
    return "Hello, " + principal.getName();
}

// Full authentication object — get authorities, credentials, details
@GetMapping("/whoami")
String whoami(Authentication auth) {
    return auth.getName() + " has " + auth.getAuthorities();
}

// From anywhere (service, scheduled job)
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```

---

## 20. Handlers vs Events vs Filters vs Controllers

| Component | Think of it as | Example |
|---|---|---|
| **Handler** | Control the authentication flow | Redirect, custom response, login/logout behavior |
| **Event + Listener** | React AFTER something happened | Audit logs, emails, notifications, metrics |
| **Filter** | Inspect EVERY request before/after it reaches controllers | JWT validation, request logging, API key validation, CORS |
| **Controller** | Handle normal business pages and APIs | `/dashboard`, `/profile`, `/orders` |

### Simple rule

- Need to change what the user receives? → **Handler**
- Need to perform additional work after an authentication event? → **Event Listener**
- Need to process every incoming request? → **Filter**

### When to use each — matrix

| Requirement | Handler | Event/Listener | Filter |
|---|:---:|:---:|:---:|
| Redirect after login | ✅ | ❌ | ❌ |
| Redirect after logout | ✅ | ❌ | ❌ |
| Show custom login error | ✅ | ❌ | ❌ |
| Save login audit | ❌ | ✅ | ❌ |
| Update last-login time | ⚠️ possible | ✅ recommended | ❌ |
| Send login email | ❌ | ✅ | ❌ |
| Publish Kafka message | ❌ | ✅ | ❌ |
| Validate JWT | ❌ | ❌ | ✅ |
| Log every request | ❌ | ❌ | ✅ |
| Add correlation ID | ❌ | ❌ | ✅ |
| Check API key | ❌ | ❌ | ✅ |

### Which extension point matches which requirement

| Requirement | Best choice |
|---|---|
| Redirect after login | `AuthenticationSuccessHandler` |
| Redirect after logout | `LogoutSuccessHandler` |
| Custom login failure page | `AuthenticationFailureHandler` |
| Audit login/logout | `AuthenticationSuccessEvent` + `@EventListener` |
| Send email after login | `@EventListener` |
| Update last-login time | Either `AuthenticationSuccessHandler` or `@EventListener` |
| Count failed login attempts | `AbstractAuthenticationFailureEvent` + `@EventListener` |

---

## 21. Interview Q&A

**Q1: What happens if I add `spring-boot-starter-security` and don't configure anything?**
> Every endpoint requires auth, one in-memory user `user` with a random password logged at startup, Spring's default `/login` page.

**Q2: How do I turn Spring Security off during development?**
> Two options: exclude `SecurityAutoConfiguration` in `@SpringBootApplication`, or provide a `SecurityFilterChain` that `permitAll()` everything. Interviewers like knowing both.

**Q3: How does Spring Security know whether to use its defaults or my config?**
> `@ConditionalOnMissingBean(SecurityFilterChain.class)` on `SecurityAutoConfiguration`. If your bean exists, the auto-config bails.

**Q4: Is `@EnableWebSecurity` required on my config class?**
> In modern Spring Boot (2.7+) it's included automatically when you're using the security starter. Adding it explicitly is harmless and makes intent clear.

**Q5: Difference between 401 and 403?**
> 401 = not authenticated (no or bad credentials). 403 = authenticated but not authorized for this resource.

**Q6: Where does Spring store the authenticated user during a session?**
> Under the `HttpSession` attribute key `SPRING_SECURITY_CONTEXT`. Not `"user"` or any name you'd guess. That's why manual `session.getAttribute("user")` returns null.

**Q7: When does `POST /login` reach the controller?**
> It doesn't. `UsernamePasswordAuthenticationFilter` in the filter chain intercepts before controllers. Any `@PostMapping("/login")` becomes dead code.

**Q8: After successful login, how does Spring know where to send the user?**
> `SavedRequestAwareAuthenticationSuccessHandler` checks `RequestCache` for a saved request. If present → redirect there. Otherwise → the fallback URL (`/` by default, or whatever `.defaultSuccessUrl(url)` sets).

**Q9: Difference between `hasRole("ADMIN")` and `hasAuthority("ROLE_ADMIN")`?**
> They mean the same thing. `hasRole(x)` automatically prepends `ROLE_` to the string. Roles are just authorities with a `ROLE_` prefix convention.

**Q10: Can a user have multiple roles?**
> Yes. `.roles("USER", "TRADER", "ADMIN")` gives three authorities. Check any with `.hasAnyRole("USER", "TRADER")`.

**Q11: What are the three customization zones in Spring Security?**
> Authentication (`UserDetailsService`, `AuthenticationProvider`, `AuthenticationManager`), Authorization (`AccessDecisionManager`, voters, `@PreAuthorize`), and Filters (`SecurityFilterChain`, custom `OncePerRequestFilter`).

**Q12: What does `AccessDecisionManager` do?**
> It combines the votes of multiple `AccessDecisionVoter`s to make the final "allow / deny" decision. Default strategy is `AffirmativeBased` — one yes is enough.

**Q13: How is the SecurityContext preserved across requests?**
> `SecurityContextHolderFilter` (was `SecurityContextPersistenceFilter` before Spring Security 6) loads the context from the session at the start of a request, and saves it back at the end. Between requests, it lives in the `HttpSession` under key `SPRING_SECURITY_CONTEXT`.

---

## 22. Recommended learning path

| Stage | Topic | What it teaches |
|:---:|---|---|
| 1️⃣ | Filter chain intuition | Security runs BEFORE your controllers |
| 2️⃣ | Defaults vs custom config | `@ConditionalOnMissingBean` mechanism |
| 3️⃣ | Minimal `SecurityFilterChain` | Reproduce defaults with your own bean |
| 4️⃣ | `.loginPage()` + custom login HTML | Take over the login page |
| 5️⃣ | `UserDetailsService` | Replace the auto-generated `user` account |
| 6️⃣ | `PasswordEncoder` (BCrypt) | Never store plaintext |
| 7️⃣ | Roles and authorities | Authorization for URLs and methods |
| 8️⃣ | `Principal` / `Authentication` / `@AuthenticationPrincipal` | Access the authenticated user |
| 9️⃣ | Handlers | Customize login/logout redirect behavior |
| 🔟 | Events & Listeners | Audit logging and post-authentication actions |
| 1️⃣1️⃣ | Custom Filters | JWT, API keys, correlation IDs |
| 1️⃣2️⃣ | Multiple `SecurityFilterChain`s | Session for browser, JWT for API |
| 1️⃣3️⃣ | OAuth2 Client | "Login with Google/GitHub" |
| 1️⃣4️⃣ | OAuth2 Resource Server | Protect APIs with tokens |

---

## The one-line summary

> **Spring Security is a filter chain that runs before your controllers to answer two questions: *who are you* (authentication) and *are you allowed* (authorization). It's auto-configured with safe-but-annoying defaults. Your `SecurityConfig` is how you replace those defaults with your own rules that match your app's actual URLs and users.**

## Q:Why do we need multiple SecurityFilterChains?

>  Different parts of an application often require different security policies. For example, a REST API may use JWT authentication, an admin portal may use OAuth2 with MFA, and a public website may allow anonymous access. Instead of applying the same filters to every request, Spring Security allows multiple SecurityFilterChains. FilterChainProxy evaluates each chain in order, selects the first one whose request matcher matches the incoming request, and executes only the filters configured for that chain
## Q what is decide FilterChainProxy?
>  FilterChainProxy decides which SecurityFilterChain to use by evaluating them one by one. @Order determines the sequence in which those chains are evaluated. FilterChainProxy does not sort or prioritize chains itself—it simply iterates over the ordered list provided by the Spring container.
## Q: Why do we need @Order if FilterChainProxy already matches the request?
> Because a single request can match more than one SecurityFilterChain. FilterChainProxy does not evaluate all matching chains and choose the most specific one. It simply iterates through the configured chains and selects the first matching chain. @Order determines that evaluation order, ensuring that more specific chains (like /admin/**) are checked before broader chains (like /**).

## Q1: Why multiple chains — can't one handle everything?
> Because different URL groups need completely different security rules that are hard to fit in one chain.
  Example: browser pages need session + CSRF + form login. REST APIs need stateless + no CSRF + JWT. If you try to do both in ONE chain, you end up with tangled if URL starts with /api logic everywhere. Two chains = clean separation.

## Q2: When do you need multiple? (example)

    Most common scenario: Browser UI + REST API in the same app.
    
    @Bean @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) {
    http.securityMatcher("/api/**")     // ← only /api/* URLs
    .csrf(c -> c.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
    .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    return http.build();
    }
    
    @Bean @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) {
    http.authorizeHttpRequests(a -> a
    .requestMatchers("/auth/**").permitAll()
    .anyRequest().authenticated())
    .formLogin(f -> f.loginPage("/auth/login"));
    return http.build();
    }
    
    Other scenarios: admin console + public site, multi-tenant app, actuator endpoints with different auth.

## Q3: Who picks which chain runs?

    FilterChainProxy. For each incoming request:
    - Goes through chains in @Order sequence (lowest number first)
    - Checks the securityMatcher(...) on each chain
    - First match wins — that chain runs, others are skipped

    So /api/holdings → matches chain #1 → API chain runs. /dashboard → doesn't match #1 → matches #2 (no matcher = catch-all) → web chain runs.
    Key rule: put the more specific chain first (lower @Order), the catch-all last.

## Q:At startup app what happen ?

    | Component             | Startup                    | Every Request                         |
    | --------------------- | -------------------------- | ------------------------------------- |
    | DelegatingFilterProxy | ✅ Created/registered       | ✅ `doFilter()` called                 |
    | FilterChainProxy      | ✅ Created as a Spring bean | ✅ `doFilter()` called                 |
    | SecurityFilterChain   | ✅ Built once               | ❌ Not rebuilt; only selected and used |
    
                        Startup
                       │
                       ▼
    Create DelegatingFilterProxy      (Once)
    
    Create FilterChainProxy           (Once)
    
    Build SecurityFilterChains        (Once)
    
    --------------------------------------------
    
                 Every HTTP Request
                       │
                       ▼
    DelegatingFilterProxy.doFilter()
    
                    ↓
                    
                    FilterChainProxy.doFilter()
                    
                    ↓
                    
                    Find Matching SecurityFilterChain
                    
                    ↓
                    
                    Execute Filters
                    
                    ↓
                    
                    DispatcherServlet

## Q: Are DelegatingFilterProxy, FilterChainProxy, and SecurityFilterChain executed only during startup or on every request?

    Configured at startup. Executed every request.

    ┌───────────────────────┬───────────────────────────────────────────────────────────────┬────────────────────────────────────────────┐
    │       Component       │                            Startup                            │               Every request                │
    ├───────────────────────┼───────────────────────────────────────────────────────────────┼────────────────────────────────────────────┤
    │ DelegatingFilterProxy │ Created once, registered with Tomcat                          │ doFilter() runs                            │
    ├───────────────────────┼───────────────────────────────────────────────────────────────┼────────────────────────────────────────────┤
    │ FilterChainProxy      │ Created once, holds the list of all SecurityFilterChain beans │ doFilter() runs — picks the matching chain │
    ├───────────────────────┼───────────────────────────────────────────────────────────────┼────────────────────────────────────────────┤
    │ SecurityFilterChain   │ Configured once (rules + filter list built)                   │ Its filters execute in order               │
    └───────────────────────┴───────────────────────────────────────────────────────────────┴────────────────────────────────────────────┘
    
    SecurityFilterChain → Defines which security filters should be applied for a matching request.
    FilterChainProxy → Selects the first matching SecurityFilterChain for the request.
    VirtualFilterChain → Executes the filters in the selected SecurityFilterChain one by one.

    So: beans built at startup, filter methods called on every incoming HTTP request.

                        Request
                           │
                           ▼
                  DelegatingFilterProxy
                           │
                           ▼
                   FilterChainProxy
                           │
          Select matching SecurityFilterChain
                           │
                           ▼
          SecurityFilterChain (Configuration)
                           │
            Contains List<Filter>
                           │
                           ▼
                  VirtualFilterChain
                           │
           Executes filters one by one
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    SecurityContext   Authentication   Authorization
    Filter             Filter           Filter

## Q: Servlet Filter vs Spring Security Filter
    
    | Servlet Filter                                                           | Spring Security Filter                                                                    |
    | ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
    | Registered with Tomcat                                                   | Registered inside `SecurityFilterChain`                                                   |
    | Runs as part of the servlet filter chain                                 | Runs inside Spring Security after `DelegatingFilterProxy`                                 |
    | Implement `Filter`                                                       | Usually extend `OncePerRequestFilter`                                                     |
    | Configured in `web.xml` (traditional) or `FilterRegistrationBean` (Boot) | Configured using `HttpSecurity.addFilterBefore()`, `addFilterAfter()`, or `addFilterAt()` |

## Q : Spring Boot auto-registers any Filter?
    Because Spring Boot auto-registers any Filter bean into Tomcat's servlet filter chain — not Spring Security's chain.
    
    Why it runs
    
    Your class has:
    - @Component → Spring makes it a bean
      - extends OncePerRequestFilter → which implements jakarta.servlet.Filter
    
    Spring Boot has a rule: any Filter bean in the context is automatically added to the servlet filter chain by Tomcat. It runs on every request, regardless of SecurityFilterChain config.
    
    Two filter chains exist (not the same thing)
    
    Request
    │
    ▼
    Tomcat's Servlet Filter chain          ← LoggingFilter runs HERE (before Spring Security)
    │
    ▼
    DelegatingFilterProxy
    │
    ▼
    Spring Security's SecurityFilterChain  ← where CsrfFilter, LogoutFilter, etc. live
    │
    ▼
    DispatcherServlet → Controller
    
    To disable auto-registration
    
    Option 1 — remove @Component.
    
    Option 2 — explicitly register and disable:
    
    @Bean
    public FilterRegistrationBean<LoggingFilter> disableLoggingFilter(LoggingFilter filter) {
    FilterRegistrationBean<LoggingFilter> bean = new FilterRegistrationBean<>(filter);
    bean.setEnabled(false);
    return bean;
    }
    
    To scope it to specific URLs only
    
    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilter(LoggingFilter filter) {
    FilterRegistrationBean<LoggingFilter> bean = new FilterRegistrationBean<>(filter);
    bean.addUrlPatterns("/api/*");  // only for /api/*
    return bean;
    }