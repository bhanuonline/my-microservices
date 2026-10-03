# 02 — End-to-end flow

Walkthroughs of two representative requests, filter-by-filter and bean-by-bean.

---

## Flow A: Admin logs in, sees the dashboard

### Step 0 — Browser hits `/admin` (unauthenticated)

```
Browser              Filter chains                        Result
──────               ─────────────                        ──────
GET /admin           Order 0: securityMatcher /admin/**   MATCH — this chain owns it
                     ↓
                     AuthorizationFilter checks role      No auth → deny
                     ↓
                     ExceptionTranslationFilter           throws AccessDeniedException
                     ↓
                     LoginUrlAuthenticationEntryPoint     Save request in HttpSession
                                                          Redirect → /login
```

Browser receives `302 Location: /login`.

### Step 1 — Browser hits `/login`

```
GET /login           Order 0: securityMatcher /admin/**   miss
                     Order 1: /oauth2/**, .well-known/**  miss
                     Order 2: anyRequest matcher          MATCH
                     ↓
                     /login is permitAll                  allowed
                     ↓
                     DefaultLoginPageGeneratingFilter     Renders Spring's default
                                                          login form (has CSRF token
                                                          + username + password
                                                          + also loads Bootstrap CSS
                                                          from /webjars — permitAll)
```

Browser shows the form.

### Step 2 — Browser POSTs `/login` with credentials

```
POST /login          Order 0: /admin/**  miss
username=admin       Order 1: oauth      miss
password=password    Order 2: anyRequest MATCH
_csrf=…              ↓
                     CsrfFilter                          Token matches session? ✔
                     ↓
                     UsernamePasswordAuthenticationFilter
                       ↓
                       AuthenticationManager
                         ↓
                         DaoAuthenticationProvider
                           ↓
                           UserDetailsService.loadUserByUsername("admin")
                             ↓ (jdbc profile bean)
                             AppUserRepository.findByUsername("admin")
                               ↓ SELECT * FROM app_user WHERE username='admin'
                             ↓
                             app_user_role JOIN — collect roles (EAGER fetch)
                             ↓
                             new CustomUserDetails(appUser)
                               .authorities = ["ROLE_ADMIN"]
                               .password = "{bcrypt}$2a$10$..."
                         ↓
                         PasswordEncoder.matches("password", "{bcrypt}$2a$10$...")
                           ↓ DelegatingPasswordEncoder reads prefix → BCrypt
                           ↓ BCrypt.matches → true
                       ↓
                       SecurityContextHolder.setAuthentication(auth)
                       ↓
                       SavedRequestAwareAuthenticationSuccessHandler
                         redirect to originally-requested URL from session
                         → /admin (from step 0's saved request)
```

Browser receives `302 Location: /admin` + session cookie.

### Step 3 — Browser GETs `/admin` again (now authenticated)

```
GET /admin           Order 0: /admin/** MATCH
Cookie: JSESSIONID   ↓
                     SecurityContextHolderFilter          restore Authentication from session
                     ↓
                     AuthorizationFilter                  hasRole('ADMIN') ✔
                     ↓
                     DispatcherServlet                    → AdminHomeController.dashboard()
                     ↓
                     returns "admin/dashboard"
                     ↓
                     ThymeleafViewResolver
                       loads templates/admin/dashboard.html
                       resolves ~{admin/layout :: nav} fragment
                       inlines Bootstrap CSS link (/webjars/…)
                     ↓
                     200 HTML
```

Browser renders dashboard with navbar + 2 cards.

---

## Flow B: Admin creates a new OAuth client

### Step 0 — Load the form

```
GET /admin/clients/new         Order 0 chain, authenticated
                               ↓
                               AdminClientController.createForm(model)
                               ↓
                               model.form = new ClientForm()
                               model.mode = "new"
                               model.allAuthMethods = [basic, post, none]
                               model.allGrantTypes  = [code, refresh, cc]
                               ↓
                               view: admin/clients/form
                                 - Thymeleaf renders empty form
                                 - includes hidden _csrf field (Spring auto-injects)
                                 - action="/admin/clients"   ← create path
```

### Step 1 — User fills form, clicks Save

Browser POSTs `/admin/clients` with:
```
clientId=my-app
clientName=My Application
clientSecret=hunter2
scopesCsv=openid,read,write
grantTypes=authorization_code
grantTypes=refresh_token
authMethods=client_secret_basic
redirectUris=http://localhost:9000/callback
_csrf=<token>
requireProofKey=on            (checkbox)
accessTokenTtlMin=5
refreshTokenTtlMin=60
```

### Step 2 — Filter chain

```
POST /admin/clients            Order 0 chain
                               ↓
                               CsrfFilter                verify token ✔
                               ↓
                               AuthorizationFilter       hasRole(ADMIN) ✔
                               ↓
                               DispatcherServlet
                                 → AdminClientController.create(form, binding, model)
```

### Step 3 — Data binding

Spring MVC binds request params → `ClientForm`:
```
form.clientId        = "my-app"
form.clientName      = "My Application"
form.clientSecret    = "hunter2"
form.grantTypes      = {"authorization_code", "refresh_token"}   (multi-value binding)
form.authMethods     = {"client_secret_basic"}
form.redirectUris    = "http://localhost:9000/callback"          (textarea → String)
form.setScopesCsv("openid,read,write")                            (called by binder)
    → form.scopes  = {"openid", "read", "write"}
form.requireProofKey = true
```

`@Valid` triggers validation: `@NotBlank clientId` ✔, `@NotBlank clientName` ✔ → `binding.hasErrors() == false`.

### Step 4 — Service call

```
service.save(form)
   ↓
   isNew = form.id blank → true
   id = UUID.randomUUID()
   ↓
   RegisteredClient.Builder b = RegisteredClient.withId(id)
       .clientId("my-app")
       .clientName("My Application")
   ↓
   passwordEncoder.encode("hunter2")   → "{bcrypt}$2a$10$xyz..."
   b.clientSecret(hash)
   ↓
   for m in {client_secret_basic}:
       b.clientAuthenticationMethod(new ClientAuthenticationMethod("client_secret_basic"))
   for g in {authorization_code, refresh_token}:
       b.authorizationGrantType(new AuthorizationGrantType(g))
   for s in {openid, read, write}:
       b.scope(s)
   for uri in splitLines("http://localhost:9000/callback"):
       b.redirectUri(uri)
   ↓
   b.clientSettings(ClientSettings.builder()
       .requireProofKey(true)
       .requireAuthorizationConsent(false)
       .build())
   ↓
   b.tokenSettings(TokenSettings.builder()
       .accessTokenTimeToLive(Duration.ofMinutes(5))
       .refreshTokenTimeToLive(Duration.ofMinutes(60))
       .build())
   ↓
   RegisteredClient rc = b.build()   ← immutable, fully-formed
   ↓
   repo.save(rc)
       ↓ JdbcRegisteredClientRepository
       ↓ serializes ClientSettings + TokenSettings to JSON via Jackson mixin
       ↓ INSERT INTO oauth2_registered_client (id, client_id, ...) VALUES (?, ?, ...)
   ↓
   log "Saved client id=<uuid> clientId=my-app (isNew=true)"
```

### Step 5 — Redirect back to list

Controller returns `"redirect:/admin/clients"`. Browser hits GET `/admin/clients`:
```
AdminClientController.list(model)
  → service.listAll()
    → jdbc.queryForList("SELECT id, client_id, ... FROM oauth2_registered_client ORDER BY client_id")
  → model.clients = [3 rows now: demo-client, m2m-client, my-app]
  → view: admin/clients/list
    → Thymeleaf loops <tr th:each="c : ${clients}"> → 3 rows in the table
```

Browser shows the updated list with the new row.

---

## Flow C: Client-credentials token request (unchanged, but for context)

Included so you see the OAuth path still works alongside the admin UI.

```
POST /oauth2/token             Order 0: /admin/** miss
Authorization: Basic bT...     Order 1: /oauth2/** MATCH
                               ↓
                               OAuth2ClientAuthenticationFilter
                                 parse Basic header → clientId=m2m-client, secret=m2m-secret
                                 ↓
                                 RegisteredClientRepository.findByClientId("m2m-client")
                                   ↓ (jdbc bean)
                                   SELECT * FROM oauth2_registered_client WHERE client_id=?
                                 ↓
                                 passwordEncoder.matches("m2m-secret", "{bcrypt}...")  ✔
                               ↓
                               OAuth2TokenEndpointFilter
                                 grant_type=client_credentials
                                 build JWT claims { sub: m2m-client, scope: [read], ... }
                                 sign with private key from signing_key table (kid=UUID)
                                 store OAuth2Authorization row in oauth2_authorization
                                 return { access_token, token_type, expires_in, scope }
```

Same DB. Different chain. No interference.

---

## Filter list — what runs for each URL

```
GET /admin/*         (Order 0)
  DisableEncodeUrlFilter
  WebAsyncManagerIntegrationFilter
  SecurityContextHolderFilter          ← restore auth from session
  HeaderWriterFilter                   ← X-Frame-Options etc.
  CorsFilter
  CsrfFilter                           ← required for POST admin actions
  LogoutFilter                         ← handles /logout
  UsernamePasswordAuthenticationFilter ← handles POST /login
  DefaultLoginPageGeneratingFilter     ← renders GET /login
  DefaultLogoutPageGeneratingFilter
  RequestCacheAwareFilter              ← saves pre-auth request → restores after login
  SecurityContextHolderAwareRequestFilter
  AnonymousAuthenticationFilter        ← sets Authentication if none
  ExceptionTranslationFilter           ← catches AccessDenied → redirect to /login
  AuthorizationFilter                  ← the actual hasRole/authenticated check

POST /oauth2/token   (Order 1)
  DisableEncodeUrlFilter
  WebAsyncManagerIntegrationFilter
  SecurityContextHolderFilter
  AuthorizationServerContextFilter     ← puts issuer/settings in a ThreadLocal
  HeaderWriterFilter
  CorsFilter
  CsrfFilter                           ← Spring turns it off for OAuth endpoints internally
  LogoutFilter
  OAuth2AuthorizationServerMetadataEndpointFilter  ← /.well-known
  OAuth2AuthorizationEndpointFilter                 ← /oauth2/authorize
  OAuth2DeviceVerificationEndpointFilter
  NimbusJwkSetEndpointFilter                        ← /oauth2/jwks
  OAuth2ClientAuthenticationFilter                  ← Basic auth for clients
  ...
  OAuth2TokenEndpointFilter                         ← /oauth2/token
  OAuth2TokenIntrospectionEndpointFilter
  OAuth2TokenRevocationEndpointFilter
  OAuth2DeviceAuthorizationEndpointFilter
```

Notice how each chain has its **own copy** of common filters (`CsrfFilter`, `AuthorizationFilter`, etc.) — they're independent `SecurityFilterChain` beans, not shared.
