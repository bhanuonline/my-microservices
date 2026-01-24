**What Spring Security actually does**

Its job:
authentication (who are you?),
authorization (what are you allowed to do?),
CSRF protection, session management,
and integration with common protocols like OAuth 2.0/OpenID Connect.

Stage	        What you learn	                                                                What it teaches
1️⃣	    Basic authentication (UserDetailsService, PasswordEncoder, SecurityFilterChain)	        How requests are intercepted and checked
2️⃣	    Authorization with roles (@PreAuthorize, SecurityExpression)	                        Controlling access by user roles
3️⃣	    CSRF, sessions, Remember‑Me	                                                            Web layer safety & state management
4️⃣	    Form login vs. HTTP Basic vs. Token‑based auth	                                        Auth mechanisms
5️⃣	    Integrating OAuth 2.0	                                                                Delegating trust to external providers
6️⃣	    Running an Authorization Server	                                                        Issuing + validating tokens
7️⃣	    Resource Server	                                                                        Protecting APIs via JWT validation

Spring Security is a filter chain that sits in front of your web application.
When any HTTP request reaches your app, Security intercepts it through a set of filters before it reaches your controller. Its two main responsibilities are:

1. Authentication–who are you?
    It checks credentials (username/password, token, etc.), determines if the user is valid, and creates an Authentication object that represents the logged‑in user.
   Authentication means verifying credentials:

    Username & password
    Token (like JWT)
    Social login (Google, GitHub, etc.)
    Certificate or key (for service-to-service auth)

2. Authorization - what are you allowed to do?
   It decides if the authenticated user can access a specific URL, method, or resource based on roles/authorities.
   Authorization checks access rights for specific resources.

Example	Who can access
    /admin/**	Only users with role ADMIN
    /user/profile	Only the logged-in user
    DELETE /products/{id}	Admins or owners only
    So HTTP Request ─► Security Filters ─► Controllers ─► Service/Repository

3. Authentication providers
   Spring Security doesn’t care how you authenticate. It delegates to an AuthenticationManager, which uses one or more AuthenticationProviders
    ex:
    Examples of providers:
    In‑memory (fixed users in code)
    JDBC/UserDetailsService (reads from DB)
    LDAP
    JWT / OAuth2 / SSO

    so ref : UsernamePasswordAuthFilter → AuthenticationManager → AuthenticationProvider

4. Authorization (access control)
      After a user is authenticated, each request carries the user’s details inside a SecurityContext.
      Security then uses your configuration rules to see whether that user can access the resource.
    Common ways to express rules:
   .authorizeHttpRequests(auth -> auth
      .requestMatchers("/public/**").permitAll()
      .requestMatchers("/admin/**").hasRole("ADMIN")
      .anyRequest().authenticated()
      )

   And at the method level:
   @PreAuthorize("hasRole('ADMIN')")
      public void deleteUser() { ... }
5. Configuration in SpringSecurity6+ (modern way)
   In older versions you used WebSecurityConfigurerAdapter.
   Now configuration is done with beans.
6. SecurityFilterChain [Inspect incoming HTTP requests to find authentication info (headers, forms, tokens)]:Defines which URLs are protected and how
7. UserDetailsService[Retrieves user info (from DB, LDAP, etc.)]:Spring needs a way to find users and their roles
7. AuthenticationManager[Core engine that authenticates users]:This component takes the credentials (from the login form or token), and uses UserDetailsService and PasswordEncoder to verify them.
8. GrantedAuthority / Roles: Once authentication is successful, the user gets an Authentication object that contains:
principal (username)
credentials (usually removed for security)
authorities (roles/permissions)
9. AccessDecisionManager :Makes final “allow/deny” decision.
10. Life of a request
   User logs in
     via form / token / header
     credentials sent to Spring Security filter

   AuthenticationManager checks them
     if valid, session or token created

   Authorization
     For any endpoint, checks roles (hasRole('ADMIN'))

   Access decision
     If allowed → controller runs; else → 403 Forbidden

FilterChainProxy	The router — decides which chain to use for each request	Traffic Controller
SecurityFilterChain	The pipeline of filters for a specific request type	Security Checkpoin

FilterChainProxy	Delegates requests to one of the configured SecurityFilterChains.
SecurityFilterChain	Defines which filters should run for certain URL patterns.

#Flow
┌─────────────────────────────┐
│         Browser / Client    │
└──────────────┬──────────────┘
│  (HTTP Request)
▼
┌─────────────────────────────┐
│  Embedded Server (Tomcat)   │
│  ─ receives the request ─   │
└──────────────┬──────────────┘
│
▼
(Registered at startup automatically)
┌──────────────────────────────┐
│ DelegatingFilterProxy        │
│ → delegates to               │
│   springSecurityFilterChain  │
└──────────────┬──────────────┘
│
▼
┌────────────────────────────────┐
│       Spring Security Filters  │
│--------------------------------│
│ 1. SecurityContextPersistence   │
│ 2. UsernamePasswordAuthFilter   │
│ 3. BasicAuth / JWT filters      │
│ 4. ExceptionTranslation         │
│ 5. FilterSecurityInterceptor    │
└──────────────┬─────────────────┘
│
[Checks authentication &           authorization rules]
│
┌───────────┴────────────┐
│                         │
┌──────▼───────┐        ┌────────▼─────────┐
│ Auth passed? │        │ Not Authenticated │
└──────┬───────┘        └────────┬─────────┘
│                         │
yes ───▼                         ▼─ no
┌────────────────────┐     Redirect or 401/403
│ DispatcherServlet   │
└────────┬───────────┘
│
▼
┌─────────────┐
│ Controller  │
└─────────────┘
│
▼
┌─────────────┐
│  Response   │
└─────────────┘
│
▼
┌─────────────┐
│  Client     │
└─────────────┘

Step 1: Authentication Phase
When it happens:
When you submit credentials (via form login, Basic, or OAuth2).

Who handles it:
AuthenticationManager delegates to one or more AuthenticationProviders.
Example: Username=admin, Password=admin
AuthenticationManager ->
DaoAuthenticationProvider ->
UserDetailsService -> loads user from memory or DB

If credentials are correct ➜ returns a fully authenticated Authentication object, stored in SecurityContextHolder (and also in HTTP Session).
If invalid ➜ throws exception → ExceptionTranslationFilter catches it → redirect to /login?error.

Step 2: Authorization Phase
Once the user is authenticated:

Every request after login passes again through the filter chain.
SecurityContextHolder already contains the authentication object.

What You Can Override
In the SpringSecurity pipeline, you can customize (override) parts in 3 main zones:

Zone	                Core Interfaces / Classes	                                                                Purpose
1. Authentication	    UserDetailsService, AuthenticationProvider, AuthenticationManager	                    Verify user credentials and build the Authentication object
2. Authorization	    AccessDecisionVoter, AccessDecisionManager, HTTP security rules, @PreAuthorize logic	Decide what an authenticated user can access
3. Protection / Filters	SecurityFilterChain, custom OncePerRequestFilter	                            Add custom security logic (tokens, audit, rate‑limit, headers)


DelegatingFilterProxy
↓ delegates to
FilterChainProxy#doFilter()
↓ finds matching SecurityFilterChain
↓ executes filters:
SecurityContextHolderFilter
UsernamePasswordAuthenticationFilter
AuthorizationFilter
ExceptionTranslationFilter
...

Client → FilterChainProxy → [ various filters ] → DispatcherServlet → Controller

**Authentication sub‑flow**
UsernamePasswordAuthenticationFilter
│
▼
AuthenticationManager
│
▼
┌────────────────────────────────────────┐
│   One or more AuthenticationProvider    │
│   (DAO, LDAP, JWT, OAuth2, custom...)   │
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

Authorization sub‑flow

FilterSecurityInterceptor
│
▼
AccessDecisionManager
│
▼
AccessDecisionVoters (role checks, expressions)
│
├──> allow → continue to controller
└──> deny  → 403 Forbidden (or redirect)

**Context lifecycle**
[ Request start ]
↓
SecurityContextPersistenceFilter loads security context
↓
Filters + controller use SecurityContextHolder.getContext()
↓
SecurityContextPersistenceFilter saves context to session
[ Response sent ]