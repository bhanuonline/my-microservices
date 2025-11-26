**SpringSecurity Fundamentals**

* What is Spring Security? Why do we need it?

        handle authentication, authorization
        Authentication (Who are you?)
            Verifies user identity (e.g., login form, Basic Auth, OAuth2, JWT).
            Supports multiple user stores (in-memory, JDBC, LDAP, custom).
        Authorization (What can you do?)
            Controls access to resources using roles or permissions.
            Uses configuration annotations like @PreAuthorize, @Secured, or HTTP path rules in config.
        Protection Against Common Attacks
            CSRF (Cross-Site Request Forgery)
            Session fixation
            Clickjacking
            Security headers (X-Content-Type-Options, X-XSS-Protection, etc.)
        Integration
            Works seamlessly with Spring frameworks.
            Supports OAuth 2.0 and OpenID Connect for SSO (Single Sign-On).

* Filter Chain concept (Security Filter Chain)

        In Spring Security, the Security Filter Chain is a fundamental concept — it defines how requests are intercepted and secured before reaching your application’s business logic.
              A Security Filter Chain is a sequence of Servlet Filters that Spring Security registers with the web application.
          Spring Security adds its filters before your application’s DispatcherServlet, meaning that security checks happen before the request reaches your controllers.
          Matching Filter Chain
              Spring Security can have multiple filter chains, each configured for specific URL patterns.

        For example:
         URL Pattern	Filter Chain
         /api/**	        REST API security settings
         /login/**	    Form login settings
         /public/**	    No security needed (permit all)

* DelegatingFilterProxy
* SecurityContext and SecurityContextHolder

      SecurityContext is an object provided by Spring Security that holds authentication and security-related information for the current user
      When Spring Security authenticates a user (through login form, JWT, etc.), it creates an Authentication object and stores it in the SecurityContextHolder.
* Authentication vs Authorization
* Principle, Credentials, GrantedAuthority
* Role vs Authority difference
* Basic flow of Authentication (Username/Password)
* Standard SecurityAutoConfiguration in SpringBoot

**Configuration Basics**

* Using SecurityFilterChain (modern Java config, no WebSecurityConfigurerAdapter)
* Configuring HttpSecurity
.authorizeHttpRequests / .antMatchers (new vs deprecated)
.formLogin, .logout, .rememberMe, .httpBasic
* Static resource handling (permit CSS/JS/images)
* Custom login and logout pages
* CSRF — what it is, how to disable/enable, when to disable
  
Authentication (Who are you)

The Authentication object lifecycle
UserDetails and UserDetailsService
AuthenticationProvider and AuthenticationManager
Password hashing — BCryptPasswordEncoder
In-memory authentication (simple setup)
JDBC authentication
Custom database authentication via JPA
Programmatic login (SecurityContextHolder, request.login())
Pre-authenticated or token-based authentication

Authorization (What you’re allowed to do)

Controlling access based on user roles/permissions.

Method-level security
@PreAuthorize, @PostAuthorize
@Secured, @RolesAllowed
Expression-based access control
Role hierarchy (ROLE_ADMIN > ROLE_USER)
Custom permission evaluators
Access decision voter mechanism

Customizing the Security Flow

Custom authentication filter
Custom authentication success & failure handlers
Custom access denied handler
Security events (AuthenticationSuccessEvent, etc.)
Redirecting users after login based on role
ExceptionHandling & entry points

Persisting Security
How to keep users authenticated across requests.

Sessions, cookies, and JSESSIONID
Remember-me authentication
Stateless vs stateful authentication
Session fixation protection
Session management (maximumSessions, invalidSessionUrl)

Advanced Topics

Custom AuthenticationProvider with complex logic (e.g., OTP, Email)
Multi-tenant authentication setup
LDAP integration
OAuth2 and OpenID Connect
OAuth2 client login (Google, GitHub, etc.)
OAuth2 authorization server
OAuth2 resource server (JWT Bearer tokens)
JWT (JSON Web Tokens) authentication (stateless API security)
CORS configuration with SpringSecurity
Security in Reactive applications (SpringWebFlux)

Real-world Application Security
Bringing everything together.

Role-based access in REST APIs
Securing endpoints via SecurityFilterChain for REST
CSRF in REST APIs (tokens / disable safely)
Using JWT for stateless microservice security
Global exception handling for authentication/authorization
Integration tests for security (MockMvc with @WithMockUser)
XSS, CSRF, Clickjacking prevention

ADVANCED SPRINGSECURITY TOPICS
1. Custom Authentication Systems
   Building a completely custom AuthenticationProvider
   Plugging in external identity systems (SAML, LDAP, ActiveDirectory)
   Multi‑factor authentication (username+OTP+email link)
   Delegating authentication to multiple providers (chain of providers)
   Dynamic authentication rules (context‑based)
2. Spring Security with JWT & Token‑Based Access
   Stateless JWT authentication / bearer tokens
   Token lifespan and refresh tokens
   Token blacklisting / revocation strategies (Redis / DB)
   Integrating JWT with SpringSecurity filter chain
   Rotating JWT signing keys, asymmetric (RSA / EC) keys
   Using Keycloak or another identity provider
3. SpringSecurity OAuth2 / OpenID Connect
   OAuth2 client login (Google, GitHub, etc.)
   Resource server setup (BearerTokenAuthenticationFilter)
   Authorization server (issuing tokens, scopes, claims)
   Custom claims, scopes, and token enhancers
   JWK (JSONWebKey) management
   Handling OIDC user information endpoints
   Integrating SpringAuthorizationServer project
   Building microservices with centralized OAuth2 provider
4. Security Context Propagation
   Propagating SecurityContext across threads / async calls
   Security in asynchronous methods (@Async)
   Distributed / reactive security context with WebFlux
   Using DelegatingSecurityContextExecutor and SecurityContextPersistenceFilter
5. Reactive SpringSecurity (WebFlux)
   SecurityFilterChain for WebFlux (ServerHttpSecurity)
   Reactive authentication manager
   JWT OAuth2 in reactive applications
   ProjectReactor context and security reactive subscribers
   Backpressure and SecurityContext lifecycles
6. Multi‑Tenancy and Dynamic Security
   Dynamic user stores per tenant (runtime UserDetailsService loading)
   Tenant‑specific roles, encryption, and schema binding
   Contextual filtering based on tenant id
   Multi‑tenant JWT claims
7. Advanced Authorization Techniques
   ABAC (Attribute‑Based Access Control)
   Policy Enforcement Points / Policy Decision Points
   Using SpringEL to evaluate authorization expressions dynamically
   Domain Object Security (ACLs – Access Control Lists)
   AclService, AclEntry, Sid, Permission
   Method security with ACL evaluations
   Fine‑grained field‑level and row‑level security
8. Custom Security Filters and the Filter Chain
   Understanding the full default filter chain (15+ filters)
   Inserting filters precisely (addFilterBefore, addFilterAfter)
   Creating pre/post authentication filters
   Context caching filter
   Writing filters for API throttling or audit logging
   Replacing UsernamePasswordAuthenticationFilter entirely
9. Spring Security in Microservices
   Centralized Authentication Service (Auth Server)
   Token relay in microservices
   OAuth2 SSO between microservices
   Gateway security (Spring CloudGateway)
   Propagating authentication across services
   Using distributed tracing with security context
   Securing gRPC endpoints or messaging (Kafka / RabbitMQ) with credentials
10. Auditing, Logging, and Monitoring
    Using AuthenticationEventPublisher for login success/failure
    Building audit trails for security events
    Correlating security context with logs (MDC logging)
    Integrating with ELK / OpenTelemetry
    Detecting brute‑force login attempts
    Real‑time security dashboards
11. Advanced Session & CSRF Management
    Hybrid stateful/stateless security (API + web app)
    Custom CsrfTokenRepository
    Session concurrency control (per user / per role)
    Session fixation and migration strategies
    Token rotation and SameSite cookies
12. Password & Credential Security
    PBKDF2, BCrypt, Argon2 password encoders
    DelegatingPasswordEncoder for algorithm upgrades
    Secrets storage, encryption, and rotation
    Hardware security modules (HSM)
    Integration with Vault / AWSKMS
13. SpringSecurity with External Systems
    SAML 2.0 Identity Provider / SP integration
    Social login with OAuth2 client flows
    Using Keycloak, Okta, or Auth0 with SpringBoot
    Integration with API Gateways (Kong, NGINX) for OAuth2 delegation
14. Authorization Server (SpringAuthorizationServer)
    Understanding Authorization Code / PKCE flows
    Implementing custom consent screens
    Building refresh‑token endpoints
    Storing clients and tokens in JDBC
    Customizing JWT claims / tokens
15. Security for REST APIs
    Stateless CORS & CSRF config
    Throttling / rate‑limiting filters
    API key authentication
    HMAC signing requests (AWS‑like)
    Versioning secured APIs
    Testing secured endpoints with MockMvc / RestAssured
16. Testing & Code Verification
    Using @WithMockUser, @WithUserDetails
    Security slice tests (@WebMvcTest + @AutoConfigureMockMvc)
    Integration tests for JWT‑secured APIs
    Penetration testing basics for Springapps
    Static analysis (SonarQube, OWASP DependencyCheck)
17. Security Hardening & Compliance
    HTTP security headers (CSP, HSTS, X‑Frame‑Options, etc.)
    CORS policies (allowed origins, credentials)
    Protecting REST endpoints from CORS misconfigurations
    Input validation to prevent XSS/SQL injection
    OWASP Top10 for Springapps
    Compliance: GDPR / PCI‑DSS / OAuth2 spec conformance
18. DevOps & Deployment Security
    Secure secrets in properties / environment variables
    Using encrypted configs (SpringCloudConfig + Vault)
    Secure SSL/TLS setup (mutual TLS)
    Container image hardening & least‑privilege runtime
    Integration with CI/CD secrets scanners
    Zero‑trust deployment patterns
19. Cutting‑Edge / Professional Topics
    Attribute‑based policies using SpEL (Policy Decision Points)
    Combining SpringSecurity with AOP for auditing
    Dynamic permission caching
    Event sourcing & security in CQRS
    Server‑sent events (SSE) & WebSocket security
    Integrating with GraphQL (method‑level and field‑level security)
    Reactive resource server with JWT validation via WebClient