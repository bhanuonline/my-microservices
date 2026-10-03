package com.example.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One class that holds ON/OFF switches for every feature in this project.
 *
 * <p>How it works:
 * <ul>
 *   <li>Spring reads every {@code features.something.enabled=true|false} line in
 *       {@code application-*.properties} and puts the values into this object.</li>
 *   <li>Every controller/service/template that cares about a feature asks Spring
 *       for this object and checks the flag before doing anything.</li>
 *   <li>Turning a feature off is a properties change + restart — no code deploy.</li>
 * </ul>
 *
 * <p>Rules we follow for every flag here:
 * <ol>
 *   <li>Default is {@code false}. A fresh checkout with no properties → nothing new
 *       is on. You have to opt in.</li>
 *   <li>Flag off = the WRITE path stops. Reads/existing DB values are kept as-is.
 *       Example: turn off account-lockout → no new locks fire, but users who were
 *       already locked stay locked until their timer expires or an admin unlocks
 *       them.</li>
 *   <li>All flag values are logged at boot (see {@code FeaturesConfig}) so you can
 *       tell at a glance what's active.</li>
 * </ol>
 *
 * <p>Templates read these directly:
 * {@code <div th:if="${features.refreshTokenRotation.enabled}">…</div>}
 */
@ConfigurationProperties("features")
@Data
public class FeatureFlags {

    private RefreshTokenRotation refreshTokenRotation = new RefreshTokenRotation();
    private Pkce pkce = new Pkce();
    private KeyRotation keyRotation = new KeyRotation();
    private Audit audit = new Audit();
    private AccountLockout accountLockout = new AccountLockout();
    private RateLimit rateLimit = new RateLimit();
    private CustomClaims customClaims = new CustomClaims();
    private Metrics metrics = new Metrics();
    private ConsentPage consentPage = new ConsentPage();
    private Dcr dcr = new Dcr();
    private RestApi restApi = new RestApi();
    private KmsKeys kmsKeys = new KmsKeys();
    private Tracing tracing = new Tracing();

    /**
     * Feature 1 — Refresh Token Rotation.
     *
     * <p>OAuth refresh tokens are long-lived. If someone steals your refresh token,
     * they can use it forever. With rotation ON, every /oauth2/token refresh call
     * gives the client a NEW refresh token and invalidates the old one. If the
     * thief tries the old one later — 400 error, and now you know something's wrong.
     */
    @Data
    public static class RefreshTokenRotation {
        /** ON: admin UI shows the checkbox and service writes the setting.
         *  OFF: admin UI hides the checkbox; TokenSettings kept at Spring defaults. */
        private boolean enabled = false;

        /** When admin creates a NEW client, should the checkbox start checked?
         *  Only matters when {@code enabled=true}. */
        private boolean defaultForNew = true;
    }

    /**
     * Feature 3 — Multi-key Signing / Rotation.
     *
     * <p>Auth-server signs JWTs with an RSA private key. If we rotate that key,
     * every previously-issued token is dead — users get logged out.
     *
     * <p>With multi-key: rotate keeps the OLD key around (SECONDARY) so old tokens
     * still verify, while the NEW key (PRIMARY) signs new tokens. Zero downtime.
     */
    @Data
    public static class KeyRotation {
        /** ON: /admin/keys page + rotate/retire buttons work.
         *  OFF: page hidden, but the read path (JWKSource) still respects any
         *       multi-key rows already in the DB — no in-flight tokens break. */
        private boolean enabled = false;

        /** Retire button appears next to a SECONDARY key only after this many days.
         *  Just a UI guardrail — service doesn't enforce, admin can retire sooner
         *  if they know all tokens signed by it have already expired. */
        private int autoRetireAfterDays = 30;
    }

    /**
     * Feature 9 — Distributed Tracing (Zipkin).
     *
     * <p>Every incoming HTTP request gets a trace_id. That id shows up in every
     * log line for the same request and in Zipkin's timeline view.
     *
     * <p>Off = Spring's tracing autoconfig disabled entirely (binding in
     * application-*.properties: {@code management.tracing.enabled=${features.tracing.enabled}}).
     */
    @Data
    public static class Tracing {
        /** ON = tracing active; OFF = no spans exported, log lines have empty trace ids. */
        private boolean enabled = false;
    }

    /**
     * Feature 11 — KMS-backed Signing Keys.
     *
     * <p>Feature 3 stored the RSA PRIVATE key as a PEM string in MySQL. Anyone with
     * DB read access could forge tokens. Bad.
     *
     * <p>With KMS: the private key lives inside HashiCorp Vault (or AWS KMS). The
     * auth-server calls Vault to sign each JWT — the private key never leaves.
     * DB compromise = attacker only sees public keys (useless for forgery).
     */
    @Data
    public static class KmsKeys {
        /** OFF: use JPA backend (private key in DB — Feature 3 behavior).
         *  ON:  use whatever {@code backend} says. */
        private boolean enabled = false;

        /** Which backend to use when {@code enabled=true}. */
        private Backend backend = Backend.JPA;

        private Vault vault = new Vault();

        public enum Backend { JPA, VAULT }

        /** Vault-specific connection details. Only used when backend=VAULT. */
        @Data
        public static class Vault {
            /** Where Vault is running. Dev mode default: http://localhost:8200. */
            private String uri = "http://localhost:8200";
            /** Vault auth token. Dev mode: 'root'. Prod: use AppRole or Kubernetes auth. */
            private String token = "root";
            /** Which secrets-engine mount to use. Standard is 'transit'. */
            private String transitPath = "transit";
            /** Name of our RSA key inside the transit engine. */
            private String keyName = "auth-server";
        }
    }

    /**
     * Feature 14 — REST API mirror.
     *
     * <p>Every browser admin action (create client, edit user, rotate key…) also
     * has a JSON REST endpoint under {@code /api/v1/admin/**}. Callers authenticate
     * with a JWT bearer token that carries scopes {@code admin.read},
     * {@code admin.write}, or {@code admin.unlock}.
     *
     * <p>Unlocks Terraform providers, K8s operators, CI-driven client rotation, and
     * future SPA admin frontends.
     */
    @Data
    public static class RestApi {
        /** ON = /api/v1/admin/** endpoints wired. OFF = 404 / 401 for those URLs. */
        private boolean enabled = false;
    }

    /**
     * Feature 12 — Dynamic Client Registration (RFC 7591).
     *
     * <p>Programmatic client onboarding. A caller with a bearer token that has scope
     * {@code client.create} can POST client metadata to {@code /connect/register}
     * and get a fresh client_id + client_secret back. Standard used by Keycloak,
     * Auth0, Okta.
     */
    @Data
    public static class Dcr {
        /** ON = /connect/register enabled (create/read/update/delete). OFF = 404. */
        private boolean enabled = false;
    }

    /**
     * Feature 8 — Custom Consent Page.
     *
     * <p>OAuth clients with {@code requireAuthorizationConsent=true} show the user
     * a "this app wants access to your profile + email" screen before granting a
     * token. Spring ships a plain-white default page; this flag swaps in our
     * Bootstrap-styled Thymeleaf version.
     */
    @Data
    public static class ConsentPage {
        /** ON = /oauth2/consent uses our template. OFF = Spring's default page. */
        private boolean enabled = false;
    }

    /**
     * Feature 10 — Prometheus Metrics.
     *
     * <p>Adds custom counters at {@code /actuator/prometheus}: tokens issued,
     * login attempts by result, lockouts, rate-limit denials, key rotations.
     * Grafana can chart per-client token rate; AlertManager can fire on brute
     * force spikes.
     */
    @Data
    public static class Metrics {
        /** ON = custom auth_* counters emit values. OFF = calls to AuthMetrics
         *  are no-ops (no counter ever registered). Spring's default HTTP + JVM
         *  metrics keep flowing regardless — those are Spring Boot autoconfig. */
        private boolean enabled = false;
    }

    /**
     * Feature 7 — Custom JWT Claims.
     *
     * <p>By default JWTs only carry standard fields (sub, iss, exp, ...). This
     * feature enriches them with useful info downstream services need:
     * <ul>
     *   <li>User grants: email, roles, uid (user's DB id)</li>
     *   <li>M2M grants: subject_type=client + authorities</li>
     * </ul>
     *
     * <p>Fine-grained toggles let you turn off individual claims for privacy
     * compliance without disabling the whole feature.
     */
    @Data
    public static class CustomClaims {
        /** ON = enrich JWT with per-subject claims. OFF = only the legacy
         *  'custom-issuer' claim (unchanged from Phase 0). */
        private boolean enabled = false;
        /** Include user's email address in tokens issued to them. */
        private boolean includeEmail = true;
        /** Include user's roles as a JSON array claim. */
        private boolean includeRoles = true;
        /** Include user's numeric DB id as claim 'uid'. */
        private boolean includeUserId = true;
    }

    /**
     * Feature 6 — Rate Limiting.
     *
     * <p>Cap request rate on /login (per IP) and /oauth2/token (per client_id) so
     * an attacker can't brute-force a password by hammering the endpoint. Uses
     * the token-bucket algorithm via Bucket4j.
     *
     * <p>Complements Feature 5 (account lockout). Lockout stops per-username
     * brute force; rate limit stops per-IP flood attacks.
     */
    @Data
    public static class RateLimit {
        /** ON = filter checks buckets, may return 429. OFF = filter passes through. */
        private boolean enabled = false;

        /** Behind a reverse proxy? {@code true} = read X-Forwarded-For first hop
         *  to identify the real client IP. {@code false} = use raw remote address
         *  (right for local dev without a proxy). */
        private boolean honorXForwardedFor = true;

        /** Bucket config for /login (per-IP). */
        private Bucket login = new Bucket(20);   // 20/min per IP
        /** Bucket config for /oauth2/token (per-client_id). */
        private Bucket token = new Bucket(120);  // 120/min per client_id

        /**
         * One bucket's config. Capacity = max burst; refill = tokens added per minute.
         *
         * <p>Example: capacity=20, refillPerMinute=20 means "up to 20 requests in a
         * burst, then 1 token every 3 seconds after that."
         */
        @Data
        public static class Bucket {
            /** Max simultaneous requests before the caller starts hitting 429. */
            private int capacity;
            /** How many tokens Bucket4j puts back into the bucket per minute. */
            private int refillPerMinute;

            public Bucket() {}
            public Bucket(int cap) {
                this.capacity = cap;
                this.refillPerMinute = cap;
            }
        }
    }

    /**
     * Feature 5 — Account Lockout.
     *
     * <p>After N failed logins for a specific username, freeze that account for
     * M minutes. Prevents password brute force. Legitimate user gets a "try
     * again at HH:MM" message; attacker can't validate password guesses while
     * locked.
     */
    @Data
    public static class AccountLockout {
        /** ON = failed-login handler increments counter, sets locked_until.
         *  OFF = no new locks fire.
         *  IMPORTANT: existing locked_until values in the DB are STILL respected
         *  when this is off. The flag controls the WRITE path, not the read path. */
        private boolean enabled = false;

        /** How many wrong-password attempts before the account is locked. */
        private int maxAttempts = 5;

        /** How long the account stays locked, in minutes. Fixed (not sliding —
         *  extra failures during a lock don't extend the wait). */
        private int lockoutMinutes = 15;
    }

    /**
     * Feature 4 — Audit Log.
     *
     * <p>Every mutation from an admin (create client, delete user, rotate key…)
     * lands as a row in the {@code client_audit} table. First question after
     * any security incident: "who changed this and when?" — this is how you
     * answer it.
     */
    @Data
    public static class Audit {
        /** ON = services write to the audit table.
         *  OFF = /admin/audit page redirects; new actions produce no rows.
         *  Existing rows never auto-deleted regardless. */
        private boolean enabled = false;

        /** How long to keep audit rows. NOT enforced yet — advisory config
         *  documenting the horizon for a future purge job. */
        private int retentionDays = 365;
    }

    /**
     * Feature 2 — PKCE Enforcement.
     *
     * <p>PKCE (Proof Key for Code Exchange) protects public clients — SPAs,
     * mobile apps, CLIs — that can't safely store a client_secret. Without
     * PKCE, anyone who intercepts the auth-code can trade it for a token.
     *
     * <p>This flag makes the admin UI REJECT saving a public client
     * (authMethods=none) with PKCE turned off.
     */
    @Data
    public static class Pkce {
        /** ON = ClientAdminService.save() rejects public clients that don't
         *       have Require PKCE checked. Error appears in a red banner. */
        private boolean enforceForPublicClients = false;

        /** ON = admin form shows an info banner suggesting PKCE for all clients
         *       (even confidential ones). Soft advisory — never blocks save. */
        private boolean warnForConfidentialClients = false;
    }
}
