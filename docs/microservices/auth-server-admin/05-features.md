# 05 — Features layered on top

Living doc — each feature added post-Phase-6 gets an entry here.

## The feature-flag convention

Every new feature ships behind a boolean in `application-<profile>.properties`. Rules:

1. **Default OFF** — the Java `FeatureFlags` class must default every flag to `false`. Explicit opt-in.
2. **Config-driven, not DB-driven** — properties file / env var / `-Dfeatures.x.enabled=…`. No feature-toggle table (yet).
3. **Log at boot** — `FeaturesConfig#logFeatureFlags` prints the resolved values so future-you can grep the log for "what's on."
4. **Fail closed** — if a feature is off, all code paths behave as if the feature never existed. No partial behaviour.
5. **DB values untouched when flag is off** — flipping off shouldn't corrupt per-row state that was set while it was on.

```
config/FeatureFlags.java          @ConfigurationProperties("features")
                                    - Data class per feature
config/FeaturesConfig.java        @EnableConfigurationProperties(FeatureFlags.class)
                                    + logFeatureFlags(ApplicationRunner)
application-jdbc.properties       features.<feature>.<setting>=<value>
application-inmemory.properties   features.<feature>.<setting>=false
```

**How templates consume it:**
```html
<div th:if="${features.refreshTokenRotation.enabled}"> ... </div>
```

**How controllers consume it:** inject `FeatureFlags`, put in the model as `features`:
```java
model.addAttribute("features", flags);
```

**How services consume it:** inject `FeatureFlags`, branch on `flags.getX().isEnabled()`.

---

## Feature 1 — Refresh token rotation (Sept 2026)

### Motivation
Reusing the same refresh token forever means a stolen refresh token grants attacker access until natural expiry. Rotation issues a new refresh token on each `/oauth2/token` refresh, invalidates the old one — a stolen-then-replayed token detectably locks out the legitimate client (or vice versa).

### Property surface
```properties
features.refresh-token-rotation.enabled=true
features.refresh-token-rotation.default-for-new=true
```

| Setting | Default | Behaviour |
|---|---|---|
| `enabled` | `false` | Master switch. Admin UI hides the toggle; service leaves `TokenSettings.reuseRefreshTokens` alone (Spring's default: `true`, i.e. reuse). |
| `default-for-new` | `true` | Only meaningful when `enabled=true`. New clients created via the admin UI default to rotation ON. Existing clients keep whatever was persisted. |

### Files added / touched
```
CREATE  config/FeatureFlags.java
CREATE  config/FeaturesConfig.java
MODIFY  application-jdbc.properties         (+ 2 feature lines)
MODIFY  application-inmemory.properties     (+ 1 explicit-off line)
MODIFY  dto/ClientForm.java                 (+ Boolean rotateRefreshTokens)
MODIFY  service/admin/ClientAdminService.java  (inject flags, gate TokenSettings)
MODIFY  controller/admin/AdminClientController.java  (inject flags, add to model, pre-check new form)
MODIFY  templates/admin/clients/form.html   (conditional checkbox block)
```

### The three states
```
┌─────────────────────────────────────────────────────────────────┐
│  flag OFF  (inmemory profile / prod deploys wanting reuse)      │
│  ────────                                                       │
│  • Admin form: no checkbox visible.                             │
│  • ClientAdminService: doesn't touch TokenSettings.             │
│  • Existing per-client rotation setting: preserved in DB.       │
│  • New clients: Spring default = reuse=true.                    │
│                                                                 │
│  flag ON + default-for-new=true  (jdbc profile default)         │
│  ─────────────────────────────                                  │
│  • Admin form: checkbox visible, pre-checked on New.            │
│  • On save: rotate=true → reuseRefreshTokens=false persisted.   │
│  • Admins can uncheck per-client to opt out.                    │
│                                                                 │
│  flag ON + default-for-new=false                                │
│  ─────────────────────────────                                  │
│  • Admin form: checkbox visible, unchecked on New.              │
│  • Admins must opt IN per client.                               │
└─────────────────────────────────────────────────────────────────┘
```

### What actually changes in the DB
`oauth2_registered_client.token_settings` is a JSON string. With rotation enabled it contains:
```json
"settings.token.reuse-refresh-tokens":false
```
Verify:
```bash
docker exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD -N \
  -e "USE authdb_jdbc; SELECT client_id, token_settings LIKE '%reuse-refresh-tokens\":false%' AS rotates FROM oauth2_registered_client;"
```

### How Spring implements rotation
When a client posts to `/oauth2/token` with `grant_type=refresh_token`:
```
OAuth2RefreshTokenAuthenticationProvider.authenticate()
  ↓
  load RegisteredClient
  ↓
  read TokenSettings.isReuseRefreshTokens()
  ↓
  if reuse=false:
    generate a new OAuth2RefreshToken
    the OLD refresh token's row in oauth2_authorization is invalidated
    return new access + new refresh
  else:
    return new access + SAME refresh
```

### Verification (manual test)

Prerequisite: get an initial refresh token via the browser auth-code flow (using demo-client). Or manually seed one.

```bash
# 1. Use refresh token — get new pair
curl -s -u 'demo-client:secret' \
  -d 'grant_type=refresh_token&refresh_token=OLD_REFRESH_TOKEN' \
  http://localhost:8095/oauth2/token
# → { access_token: ..., refresh_token: NEW_REFRESH_TOKEN }

# 2. Try OLD refresh again — should fail
curl -s -u 'demo-client:secret' \
  -d 'grant_type=refresh_token&refresh_token=OLD_REFRESH_TOKEN' \
  http://localhost:8095/oauth2/token
# → { error: invalid_grant }

# 3. NEW refresh works
curl -s -u 'demo-client:secret' \
  -d 'grant_type=refresh_token&refresh_token=NEW_REFRESH_TOKEN' \
  http://localhost:8095/oauth2/token
# → 200 { access_token, refresh_token: NEWEST }
```

### Trade-offs

| Rotation ON | Rotation OFF (reuse) |
|---|---|
| Breach detection (replay → 401) | No breach signal until access token expires |
| Multi-tab / concurrent-request clients can race and self-invalidate | Idempotent — any client thread can refresh |
| Requires careful client bookkeeping | Simpler client code |

### Interview line
"We turned on refresh token rotation for automatic replay-attack detection. It's per-client (`TokenSettings.reuseRefreshTokens`), gated by a feature flag so we can flip it off across the whole fleet if concurrent-client bugs surface. Default-for-new keeps new clients safe without operator effort; existing clients aren't touched until an admin explicitly saves them."

---

## Feature 2 — PKCE enforcement for public clients (Sept 2026)

### Motivation
Public clients — SPAs, mobile apps, CLIs — can't store a client_secret safely, so they authenticate with `ClientAuthenticationMethod.NONE`. Without PKCE, anyone who intercepts the auth-code (malicious mobile app hijacking a custom URL scheme, compromised redirect logger, network eavesdrop) can trade it for a token. **PKCE binds the code to a secret only the legitimate client holds**, so a stolen code is useless.

### The attack that PKCE stops
```
Attacker registers a malicious app on the same OS that claims the same
custom URL scheme (myapp://oauth-callback). Legitimate app starts an
auth-code flow; browser redirects to myapp://oauth-callback?code=…
The OS delivers the redirect to the malicious app. Attacker POSTs
/oauth2/token with the stolen code → token. Legitimate user is silently
compromised.

With PKCE:
  Malicious app has the code but not the code_verifier (a random string
  the legitimate app generated in memory). POST /oauth2/token requires
  BOTH → attacker's exchange returns invalid_grant.
```

### Property surface
```properties
features.pkce.enforce-for-public-clients=true
features.pkce.warn-for-confidential-clients=true
```

| Setting | Default | Behaviour |
|---|---|---|
| `enforce-for-public-clients` | `false` | On save, reject any client whose `authMethods` contains `none` if `requireProofKey=false`. |
| `warn-for-confidential-clients` | `false` | Show an OAuth 2.1 best-practice info banner on the form. Soft — never blocks save. |

### Enforcement point: admin save, not runtime

Two places we could enforce:
1. **Admin save time** (what we do) — the `ClientAdminService.validatePkce()` method runs on every create/update. Bad config never reaches the DB.
2. **Runtime via a custom `TokenEndpointFilter`** — allow any config, reject the actual auth flow.

We chose #1 because:
- Feedback is immediate to the admin — they see the red banner as they're creating the client.
- No runtime surprises for end-users of a badly-configured client.
- Simpler code — no extra filter, no OAuth flow knowledge required.

### Defense-in-depth
`ClientAdminService.save()` also calls `validatePkce()` and throws `IllegalArgumentException` if it fails. So even a hypothetical future controller that skips the check can't persist an insecure public client.

### Files touched
```
MODIFY  config/FeatureFlags.java                    +Pkce nested class
MODIFY  config/FeaturesConfig.java                  log Pkce values at boot
MODIFY  application-jdbc.properties                 +features.pkce.*
MODIFY  application-inmemory.properties             +features.pkce.*=false
MODIFY  service/admin/ClientAdminService.java       +validatePkce(); guard in save()
MODIFY  controller/admin/AdminClientController.java call validatePkce; pre-check on new
MODIFY  templates/admin/clients/form.html           global-errors banner + soft warning
```

### The four states
```
┌─────────────────────────────────────────────────────────────────┐
│  enforce=false  warn=false                                      │
│  ─────────────                                                  │
│  Feature is a no-op. Admin can create any client, any config.   │
│                                                                 │
│  enforce=false  warn=true                                       │
│  ─────────────                                                  │
│  Info banner on form: "OAuth 2.1 recommends PKCE for all".      │
│  No enforcement. Nudge, not block.                              │
│                                                                 │
│  enforce=true   warn=false                                      │
│  ─────────────                                                  │
│  Public client without PKCE → save rejected with red banner.    │
│  Confidential clients: no message.                              │
│  New-client form: Require PKCE pre-checked.                     │
│                                                                 │
│  enforce=true   warn=true                                       │
│  ─────────────                                                  │
│  Both. Public: hard-block. Confidential: soft banner.           │
│  Recommended production setting.                                │
└─────────────────────────────────────────────────────────────────┘
```

### Verification
```bash
# 1. Boot log shows all 4 flags now
mvn -pl infra/auth-server spring-boot:run -Dspring-boot.run.profiles=jdbc
# ================= Feature flags =================
# refresh-token-rotation.enabled         = true
# refresh-token-rotation.default-for-new = true
# pkce.enforce-for-public-clients        = true
# pkce.warn-for-confidential-clients     = true
# =================================================

# 2. Try creating a public client WITHOUT PKCE:
#    UI: clientId=test-public, authMethods=[none], grantTypes=[authorization_code]
#    Uncheck Require PKCE, submit.
#    → red banner: "Public clients (authentication method = none) must have Require PKCE enabled."
#    → DB unchanged.

# 3. Check Require PKCE, submit.
#    → 302 to /admin/clients, new row.
docker exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD -N \
  -e "USE authdb_jdbc; SELECT client_id, client_settings LIKE '%require-proof-key\":true%' AS pkce FROM oauth2_registered_client WHERE client_id='test-public';"
# → test-public  1

# 4. Flip flag off:
#    features.pkce.enforce-for-public-clients=false
#    Restart, retry step 2 — save succeeds. Existing test-public row untouched.
```

### Trade-offs

| Enforce ON | Enforce OFF |
|---|---|
| Public clients can never be misconfigured | Legacy clients that predate PKCE keep working |
| Some old JS OAuth libs don't emit PKCE — must upgrade | Broader compatibility |
| Attack surface narrower by construction | Have to rely on client-side discipline |

### Interview line
"We enforce PKCE at admin save-time, not runtime. Two reasons: immediate feedback to the operator (they can't create a broken client without knowing) and defense-in-depth — the service layer double-checks even if a controller forgets to. The feature flag lets a fleet-wide rollout be a config change, not a code deploy, and the strict enforcement is intentional — you can't opt out of a security control safely."

---

## Feature 3 — Multi-key signing + rotation (Sept 2026)

### Motivation
Single-key signing means every rotate = every user gets logged out (JWTs signed with the old kid can't be verified). Multi-key = "old key still verifies existing tokens while new key signs new ones" — zero-downtime rotation.

### The three states
```
PRIMARY    signs NEW tokens                 exactly one row
SECONDARY  verifies EXISTING tokens         zero or more rows, in JWKS
RETIRED    kept for audit only              removed from JWKS
```

### Migration (V7)
```sql
ALTER TABLE signing_key ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PRIMARY';
UPDATE signing_key SET status = CASE WHEN active = 1 THEN 'PRIMARY' ELSE 'RETIRED' END;
```
The existing single row becomes `PRIMARY`. `active` column keeps its meaning: `active=1` → key appears in JWKS.

### Property surface
```properties
features.key-rotation.enabled=true
features.key-rotation.auto-retire-after-days=30
```
| Setting | Default | Behaviour |
|---|---|---|
| `enabled` | `false` | Master switch for the UI + rotate/retire endpoints. When off, the read-path (`JWKSource`) still respects multiple keys in the DB — tokens signed under a rotated key don't break just because the flag flipped off. |
| `auto-retire-after-days` | `30` | Advisory. UI shows the Retire button only when a SECONDARY key is older than this. Service does NOT enforce — admin may retire sooner if they know all tokens have expired. |

### JWKSource strategy — DB per request
We rewrote `JdbcSecurityConfig.jwkSource()` from a boot-time immutable `JWKSet` to a callback that queries the DB on every sign/verify call. Trade-off:
- Pro — rotation is instant, no cache invalidation, single-instance safe.
- Con — 1 DB round-trip per token operation. Fine ≤10 tokens/sec; for higher, wrap in a Caffeine cache (1-min TTL).

Alternatives we rejected: cached-with-TTL (rotation propagation delay), volatile-in-memory (needs pub/sub for multi-instance).

### How Nimbus picks the signing key
PRIMARY rows get `KeyUse.SIGNATURE` when converted to `RSAKey`. Spring's `NimbusJwtEncoder` uses a `JWKSelector` that filters by `use=sig` for sign operations. Non-PRIMARY rows have no `use` set, so they're eligible for verification (by `kid` match) but never picked for signing.

### Files touched
```
CREATE  db/migration/V7__signing_key_multi.sql
CREATE  controller/admin/AdminSigningKeyController.java
CREATE  service/admin/KeyRotationService.java
CREATE  templates/admin/keys/list.html

MODIFY  config/FeatureFlags.java             +KeyRotation nested class
MODIFY  config/FeaturesConfig.java           log 2 more lines
MODIFY  application-jdbc.properties          +features.key-rotation.*
MODIFY  application-inmemory.properties      +features.key-rotation.enabled=false
MODIFY  entity/SigningKeyEntity.java         +Status enum field
MODIFY  repository/SigningKeyRepository.java +findFirstByStatus, findAllByActiveTrue…
MODIFY  config/JdbcSecurityConfig.java       rewrite jwkSource → DB-per-request
MODIFY  templates/admin/layout.html          +conditional Keys nav link
```

### Invariants + guardrails
| Invariant | How enforced |
|---|---|
| At most one PRIMARY | `rotate()` demotes existing PRIMARY inside a single `@Transactional` |
| Cannot retire PRIMARY | `retire()` throws `IllegalStateException` on PRIMARY |
| At least one PRIMARY exists | `jwkSource()` bootstrap creates one if the table is empty |
| Retire is idempotent | Second call on RETIRED is a no-op |

### Trap I hit
Hibernate 6 with `@Enumerated(EnumType.STRING)` and no `columnDefinition` maps to a MySQL native `ENUM(...)` column, but our V7 migration created `VARCHAR(16)`. `ddl-auto=validate` refused to boot. Fix: `@Column(columnDefinition = "VARCHAR(16)")` on the field. Otherwise you have to either (a) drop the enum and use `String`, or (b) generate an actual native ENUM in the migration.

### Verification
```bash
# 1. Boot log shows key-rotation flags.
# 2. Open /admin/keys → 1 row: PRIMARY (from Feature 1 boot).
# 3. Click Rotate → 2 rows: new PRIMARY (top), old row now SECONDARY.
# 4. Curl JWKS: 2 keys, only new one has `use:"sig"`:
curl -s http://localhost:8095/oauth2/jwks | jq '.keys[] | {kid, use}'
#   → [{ kid: <new>, use: "sig" }, { kid: <old>, use: null }]

# 5. New tokens carry the new kid:
curl -s -u m2m-client:m2m-secret -d grant_type=client_credentials \
  http://localhost:8095/oauth2/token | jq -r .access_token | cut -d. -f1 | base64 -d | jq .kid

# 6. Click Retire on SECONDARY: (only if age > auto-retire-after-days; button only appears then)
# 7. JWKS shrinks back to 1 key. Any lingering tokens signed by the retired key stop verifying.
```

### Interview line
"We split the signing lifecycle into PRIMARY/SECONDARY/RETIRED so rotation is zero-downtime. The `JWKSource` reads the DB on every operation — dead simple, wrong for high scale, exactly right for a learning project + audit-friendly. If we needed scale, wrap in Caffeine with a 1-min TTL — rotation propagation slows to 60s but cache is cheap. For multi-instance, use pub/sub to broadcast an invalidation. This design keeps that door open — the callback interface doesn't leak the implementation."

---

## Feature 4 — Audit log (Sept 2026)

### Motivation
> "Who added this client?" is the first question after any security incident. Without audit, no answer exists — git blame is meaningless once the runtime is out of code control. Feature 4 populates the `client_audit` table (created empty by V5 back in Phase 3) so every mutation has a durable record.

### Schema evolution (V8)
V5 created `client_audit(actor, action, client_id, changed_at, diff_json)` — good for clients only. V8 broadens it:
```sql
ALTER TABLE client_audit
    CHANGE COLUMN client_id subject_id VARCHAR(200),
    ADD COLUMN subject_type VARCHAR(16) NOT NULL DEFAULT 'CLIENT' AFTER action,
    ADD INDEX idx_audit_subject (subject_type, subject_id),
    ADD INDEX idx_audit_actor   (actor),
    ADD INDEX idx_audit_changed (changed_at);
```
Table name stays `client_audit` to preserve V5's readability in history. The entity is `AuditEntry` — semantic name lags physical.

### Property surface
```properties
features.audit.enabled=true
features.audit.retention-days=365
```
| Setting | Default | Behaviour |
|---|---|---|
| `enabled` | `false` | Master switch. Off → services skip audit writes; `/admin/audit` redirects to `/admin`. |
| `retention-days` | `365` | Advisory only. No purge job yet — documented so a future `@Scheduled` cleanup respects it. |

### The 5 design decisions
Written into code:
| # | Choice | Rationale |
|---|---|---|
| 1 | One `client_audit` table for CLIENT / USER / KEY via `subject_type` column | Single retention policy, single query for cross-subject investigations |
| 2 | Synchronous — audit write runs in the caller's `@Transactional` | If either the business or audit write fails, both roll back — no orphan state |
| 3 | Snapshot the new state as JSON, no field-level diff | Cheapest correct option; consumers can diff consecutive rows if they need to |
| 4 | Explicit `audit.recordClient(...)` calls, not AspectJ pointcuts | Visible in the code, easy to grep, no magic |
| 5 | Password + client secret stripped before serialization | Audit table must never leak credentials — see `snapshotForAudit()` in ClientAdminService |

### Files touched
```
CREATE  db/migration/V8__audit_expand.sql
CREATE  entity/AuditEntry.java
CREATE  repository/AuditEntryRepository.java
CREATE  service/admin/AuditService.java
CREATE  controller/admin/AdminAuditController.java   /admin/audit
CREATE  templates/admin/audit/list.html

MODIFY  config/FeatureFlags.java                  +Audit nested class
MODIFY  config/FeaturesConfig.java                log audit flags
MODIFY  application-jdbc.properties               +features.audit.*
MODIFY  application-inmemory.properties           +features.audit.enabled=false
MODIFY  service/admin/ClientAdminService.java     +audit.recordClient on save/delete
MODIFY  service/admin/UserAdminService.java       +audit.recordUser on save/delete
MODIFY  service/admin/KeyRotationService.java     +audit.recordKey on rotate/retire
MODIFY  templates/admin/layout.html               +conditional Audit nav link
```

### Actions covered
```
Subject   Actions
────────  ──────────────────────────
CLIENT    CREATE  UPDATE  DELETE
USER      CREATE  UPDATE  DELETE
KEY       ROTATE  RETIRE
```

### Behaviour when the flag is off
`AuditService.record()` is a no-op — nothing writes. Existing rows in the table stay untouched. `/admin/audit` redirects to `/admin`. Nav link disappears. Flip back on: new actions record again from that moment; you can't reconstruct the gap.

### Transactional guarantee
Both writes happen in a single transaction. Example — client CREATE:
```
1. BEGIN TX
2. INSERT INTO oauth2_registered_client ...    ← Spring's JdbcRegisteredClientRepository
3. INSERT INTO client_audit ...                ← AuditService.recordClient
4. COMMIT (or ROLLBACK — both together)
```

### Credential hygiene
- `ClientAdminService.snapshotForAudit()` explicitly excludes `clientSecret`.
- `UserAdminService.save()` builds an audit map that never includes `password`.
- If a future field is added that shouldn't be audited, extend the snapshot method — don't `mapper.valueToTree(form)` blindly.

### Verification
```bash
# 1. Boot log shows the new flags.
# 2. GET /admin/audit → "No audit entries yet".
# 3. Create a client via UI → /admin/audit shows: CREATE CLIENT <clientId>
# 4. Edit the same client → /admin/audit shows: UPDATE CLIENT <clientId>
# 5. Rotate the signing key → /admin/audit shows: ROTATE KEY <new-kid>
# 6. Verify DB rows:
docker exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD -N -e "
  USE authdb_jdbc;
  SELECT actor, action, subject_type, subject_id, changed_at
    FROM client_audit ORDER BY id;
"
# 7. Flip features.audit.enabled=false, restart. New actions produce no rows.
```

### Interview line
"Every mutating admin action lands in the audit table inside the same transaction as the business write — either both commit or both roll back. Table has one row per action, snapshot of the new state in JSON, credentials stripped. Feature flag controls the write path; existing rows are preserved when it flips off, so you never lose historical evidence. Retention-days is advisory today; when we add a scheduled purge, it becomes enforcement."

---

## Feature 5 — Account lockout (Sept 2026)

### Motivation
Without a lock, an attacker can bang on `POST /login` at whatever rate the network allows. Even BCrypt-hashed passwords cave under offline attack if the DB leaks — the DB is only one line of defense. Account lockout adds a rate wall that's per-username: N failures → freeze for T minutes. Legitimate users see a clear "try again at HH:MM"; attackers slow down enough to be caught by monitoring.

### Schema evolution (V9)
```sql
ALTER TABLE app_user
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP NULL DEFAULT NULL;
```
- `failed_attempts` — running count since last success.
- `locked_until` — when the freeze expires. NULL = not locked.

### Property surface
```properties
features.account-lockout.enabled=true
features.account-lockout.max-attempts=5
features.account-lockout.lockout-minutes=15
```

### The 5 design decisions
| # | Choice | Rationale |
|---|---|---|
| 1 | Fixed (not sliding) lockout duration | Prevents attacker's continued knocks from extending the legitimate user's wait |
| 2 | Reset counter both on success AND when lockout expires | User's punishment ended; they start fresh from 0 |
| 3 | Custom failure handler routes `LockedException` to `/login?locked` | Spring's default login page shows a specific message for `?locked` |
| 4 | Admin accounts are lockable (documented SQL escape hatch) | Consistency; break-glass is admin's responsibility |
| 5 | Unlock button visible when EITHER `locked_until` set OR `failed_attempts > 0` | Admin can help even before lockout hits |

### How Spring Security's login hooks were extended
```
POST /login
   ↓
UsernamePasswordAuthenticationFilter
   ↓ AuthenticationManager
   ↓
   DaoAuthenticationProvider.retrieveUser()
      → UserDetailsService.loadUserByUsername()  (returns CustomUserDetails)
   ↓ CHECK: userDetails.isAccountNonLocked()
   ↓        ← reads appUser.getLockedUntil() (naturally flag-neutral —
   ↓          if flag off, nothing writes locked_until, so always null)
   ↓ CHECK: passwordEncoder.matches()
   ↓
   ┌────────────────────────────────────────────────────────────┐
   │  SUCCESS  → LockoutAuthenticationHandler.onSuccess()       │
   │                → LockoutTracker.onSuccess(username)        │
   │                → resets failed_attempts = 0                │
   │                                                            │
   │  BadCredentialsException → onFailure()                     │
   │                → LockoutTracker.onFailure(username)        │
   │                → increment; if >= max → set locked_until   │
   │                → audit.recordUser("LOCK", ...)             │
   │                                                            │
   │  LockedException  → onFailure() (routes to /login?locked)  │
   └────────────────────────────────────────────────────────────┘
```

### Flag-neutral read path (subtle but important)
`CustomUserDetails.isAccountNonLocked()` reads `appUser.getLockedUntil()` unconditionally. That doesn't break flag-off boots because:
- Flag off → `LockoutTracker.onFailure` is a no-op → nothing writes `locked_until` → column stays NULL → `isAccountNonLocked` returns true.
- If someone flipped flag on temporarily, some rows may hold non-null `locked_until`; those users stay locked until timestamp expires or admin unlocks. Same policy as Feature 3 (key-rotation) and Feature 4 (audit).

### Wiring into the DEFAULT chain, not the admin chain
`/login` is handled by `SecurityConfig.defaultSecurityFilterChain` (Order 2 — shared across profiles), NOT the admin chain. The handler bean is `@Profile("jdbc")` so it only exists on that profile. We inject via `ObjectProvider<LockoutAuthenticationHandler>`:
```java
var handler = lockoutHandlerProvider.getIfAvailable();
if (handler != null) {
    fl.successHandler(handler).failureHandler(handler);
}
```
Inmemory profile → `getIfAvailable()` returns null → Spring's default handlers used. Clean profile isolation without duplicating the chain.

### Files touched
```
CREATE  db/migration/V9__app_user_lockout.sql
CREATE  security/LockoutTracker.java
CREATE  security/LockoutAuthenticationHandler.java

MODIFY  config/FeatureFlags.java                    +AccountLockout nested class
MODIFY  config/FeaturesConfig.java                  log lockout flags
MODIFY  application-jdbc.properties                 +features.account-lockout.*
MODIFY  application-inmemory.properties             +features.account-lockout.enabled=false
MODIFY  entity/AppUser.java                         +failedAttempts, lockedUntil
MODIFY  user/CustomUserDetails.java                 isAccountNonLocked() reads lockedUntil
MODIFY  config/SecurityConfig.java                  wire handler via ObjectProvider
MODIFY  service/admin/UserAdminService.java         +unlock() method
MODIFY  controller/admin/AdminUserController.java   +POST /admin/users/{id}/unlock
MODIFY  templates/admin/users/list.html             +lock badge + Unlock button
```

### Verification
```bash
# 1. Boot log: 3 new flag lines (account-lockout.enabled/max/minutes).
# 2. Log OUT the admin session (POST /logout).
# 3. Log in with wrong password 5 times as an existing user.
#    5th failure → account locked.
# 4. 6th attempt with CORRECT password → redirected to /login?locked (still locked).
# 5. DB row shows failed_attempts=5, locked_until=now+15min.
# 6. Admin logs in (admin/password — different user, not affected), goes to /admin/users.
#    Sees badge "LOCKED" next to test user's username + Unlock button.
# 7. Click Unlock → DB cleared → test user can log in.
# 8. Audit table has LOCK + UNLOCK rows.
```

### The escape hatch for a locked admin
Only admin can unlock. If the only admin locks themselves out:
```bash
docker exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD -e "
  USE authdb_jdbc;
  UPDATE app_user SET failed_attempts=0, locked_until=NULL
  WHERE username='admin';
"
```
Real prod: emergency break-glass account, or the always-uncroakable K8s-cronjob-with-DB-access, or a bootstrap CLI. Out of scope here.

### Trade-offs

| Lockout ON | Lockout OFF |
|---|---|
| Brute force ≤ 1 / T minutes per user | Attacker only limited by network |
| Legitimate user gets frozen out if they fat-finger | User keeps trying, no wall |
| DoS vector: attacker can lock any known username | No DoS via login endpoint |
| Requires monitoring for spike-in-locks | No admin alerts |

The DoS concern (deliberately-lock-a-victim) is real. Mitigations for a future feature: don't lock, just add exponential backoff per IP; or lock a "shadow" state that still lets the real user in from a known device. For learning, the classic per-user counter is the right teaching artifact.

### Interview line
"Lockout is a per-username counter that fires on N consecutive failures for T minutes. We hook Spring's success+failure handlers to keep the counter and set the timestamp; `isAccountNonLocked` reads the timestamp, so the enforcement is automatic. The feature flag controls the write path — flip it off, no new locks; existing rows still respected until they expire. Admin can force-clear via the users page. The classic DoS-a-known-victim vector is real; a next-gen version would layer IP-based backoff on top, but that's a rate-limiting feature, not lockout."

---

## Feature 6 — Rate limiting (Sept 2026)

### Motivation
Account lockout defends against brute-force on a single username. It does NOT defend against attackers who rotate usernames — no single user hits the fail threshold, so nobody ever locks. Rate limiting is the complement: a hard ceiling per IP (or per client_id) on request rate, regardless of what the payload contains. Together they make brute force uneconomic.

### Algorithm — token bucket (Bucket4j)
```
Bucket capacity = 20 tokens
Refill = 20 tokens / minute (roughly 1 every 3 seconds)

Request arrives → try to consume 1 token
  ├─ success   → let through
  └─ empty     → HTTP 429 + Retry-After header, no downstream work
```
Constant memory per key, natural burst tolerance, no time-window edge cases.

### What's protected
```
POST /login          → 20 req/min per IP
POST /oauth2/token   → 120 req/min per client_id (falls back to IP if no Basic auth)
```
GET requests, admin UI, JWKS, discovery — untouched. Only the two write endpoints that do expensive work.

### Property surface
```properties
features.rate-limit.enabled=true
features.rate-limit.honor-x-forwarded-for=true
features.rate-limit.login.capacity=20
features.rate-limit.login.refill-per-minute=20
features.rate-limit.token.capacity=120
features.rate-limit.token.refill-per-minute=120
```
| Setting | Default | Behaviour |
|---|---|---|
| `enabled` | `false` | Master switch. Off → filter is a pass-through. |
| `honor-x-forwarded-for` | `true` | Behind a load balancer, use the first-hop of XFF; else fall back to `getRemoteAddr()`. |
| `login.capacity` / `refill-per-minute` | `20` / `20` | Human login: modest burst OK. |
| `token.capacity` / `refill-per-minute` | `120` / `120` | M2M can be chattier. |

### 5 design decisions
| # | Choice | Rationale |
|---|---|---|
| 1 | ConcurrentHashMap (in-JVM), not Redis | Learning project + single instance. Interface would stay identical if we swapped in Caffeine (bounded memory) or Redis (multi-instance shared state). |
| 2 | Extract IP from XFF first, fall back to `getRemoteAddr()` | Standard reverse-proxy pattern. Flag turns off for dev without a proxy. |
| 3 | Per-client_id for /oauth2/token, per-IP for /login | Multiple M2M clients from one IP shouldn't share a bucket; humans have one IP per session. |
| 4 | Filter registered at `Ordered.HIGHEST_PRECEDENCE` (before Spring Security) | Reject fast, no CSRF or password-hash work when limit hit. |
| 5 | JSON body + `Retry-After` header | Standard machine-readable format for both endpoints. |

### Files added / touched
```
CREATE  security/RateLimitService.java              ConcurrentHashMap of Bucket4j buckets
CREATE  security/RateLimitFilter.java               OncePerRequestFilter at HIGHEST_PRECEDENCE

MODIFY  auth-server/pom.xml                         +com.bucket4j:bucket4j-core:8.10.1
MODIFY  config/FeatureFlags.java                    +RateLimit nested class (with Bucket subrecord)
MODIFY  config/FeaturesConfig.java                  log rate-limit flags
MODIFY  application-jdbc.properties                 +features.rate-limit.*
MODIFY  application-inmemory.properties             +features.rate-limit.enabled=false
```

### Verification
```bash
# 1. Boot log shows rate-limit flags.
# 2. Blast /login with wrong password 21 times from same IP:
for i in $(seq 1 21); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -d "username=locktest&password=wrong&_csrf=$CSRF" \
    -b /tmp/cookies.txt http://localhost:8095/login
done
# → first 20:  302  (redirected to /login?error)
# → 21st:      429  with Retry-After header

# 3. Same for /oauth2/token (121 requests, 121st = 429).

# 4. Wait ~3s for a token to refill → next request succeeds again.

# 5. features.rate-limit.enabled=false → no 429s ever, filter passes through.
```

### Trade-offs

| Rate limit ON | Rate limit OFF |
|---|---|
| Brute force impossible | Attacker only limited by network |
| Legitimate spikes rejected (e.g. deploy triggers many M2M refreshes) | No wall for accidents either |
| Per-IP: NAT'd users share bucket (office / mobile carriers) | No such collateral |
| Storage grows with unique keys (fine short-term) | Zero overhead |

### The NAT problem (worth mentioning)
Per-IP limiting punishes users behind shared NAT (corporate proxies, mobile carriers). Mitigations for a future feature: per-IP + per-session hybrid, or drop-to-CAPTCHA at the wall instead of hard 429. Out of scope for this iteration.

### Interview line
"Two-endpoint token bucket: /login per-IP at 20/min, /oauth2/token per-client_id at 120/min. Runs at `HIGHEST_PRECEDENCE` — rejects before Spring Security does any password work. Storage is a `ConcurrentHashMap<String, Bucket>` — production would swap in Caffeine for bounded memory or Redis for cross-instance state; the service interface doesn't change. Feature flag controls only the write path — flip it off, filter becomes a no-op, no code deploy needed."

---

## Feature 7 — Custom JWT claims (Sept 2026)

### Motivation
Without custom claims, every resource-server has to call `/userinfo` (or hit the DB itself) to know the user's email, roles, or ID. That's a round-trip on every protected request. Custom claims put the useful fields INSIDE the JWT so downstream services parse them from the token they already verify.

### The customization hook
Spring Authorization Server publishes `OAuth2TokenCustomizer<JwtEncodingContext>`. Every token — access, ID, refresh — passes through this bean's `customize()` method with a mutable `JwtEncodingContext.getClaims()` builder. We inject flag-gated logic per grant type:

```
authorization_code / refresh
   principal = CustomUserDetails
   → emit: email, roles, uid (per-claim toggles)

client_credentials
   principal = the client itself (no user)
   → emit: authorities (the client's granted authorities)
```

Both grants still get the legacy `custom-issuer` claim (pre-Feature-7 behaviour, unchanged).

### Property surface
```properties
features.custom-claims.enabled=true
features.custom-claims.include-email=true
features.custom-claims.include-roles=true
features.custom-claims.include-user-id=true
```
| Setting | Default | Purpose |
|---|---|---|
| `enabled` | `false` | Master switch. Off → tokens only get `custom-issuer` (today's behaviour). |
| `include-email` | `true` | Per-claim toggle — comply with privacy asks like "no email in tokens for this region". |
| `include-roles` | `true` | Turn off if authorization is centralised in a policy service. |
| `include-user-id` | `true` | Turn off if resource-servers should treat `sub` as opaque. |

Fine-grained toggles are the interview goldmine: "we let each claim be flipped off per-environment for compliance".

### Where profile-safety comes in
`SecurityConfig.tokenCustomizer` is a SHARED bean (both inmemory and jdbc profiles). But `FeatureFlags` is currently only configured for jdbc-profile properties. To avoid a `NoSuchBeanDefinitionException` on inmemory boot, we inject via `ObjectProvider<FeatureFlags>` — `getIfAvailable()` returns null on inmemory, tokens fall through to legacy behaviour.

Actually, `FeatureFlags` is registered via `@EnableConfigurationProperties` and works on BOTH profiles (property values differ). But the `ObjectProvider` pattern is still the right idiom for "fail gracefully if optional".

### Files touched
```
MODIFY  config/FeatureFlags.java             +CustomClaims nested class
MODIFY  config/FeaturesConfig.java           log 4 new flags
MODIFY  application-jdbc.properties          +features.custom-claims.*
MODIFY  application-inmemory.properties      +features.custom-claims.enabled=false
MODIFY  user/CustomUserDetails.java          +getAppUser() accessor
MODIFY  config/SecurityConfig.java           tokenCustomizer takes ObjectProvider<FeatureFlags>
                                             and enriches claims per grant type
```

### Verification
```bash
# 1. Boot log lines
#    custom-claims.enabled              = true
#    custom-claims (email/roles/uid)    = true/true/true

# 2. Grab a client_credentials JWT and decode the payload:
TOK=$(curl -s -u m2m-client:m2m-secret \
        -d grant_type=client_credentials -d scope=read \
        http://localhost:8095/oauth2/token | jq -r .access_token)
echo "$TOK" | cut -d. -f2 | base64 -d 2>/dev/null | jq .
#
# BEFORE Feature 7:  { sub, aud, nbf, scope, iss, exp, iat, jti, custom-issuer }
# AFTER  Feature 7:  ...same plus "authorities": [ ... ]
#
# (m2m-client has no explicit authorities beyond what its client_credentials grant
# gives it, so 'authorities' may still be empty — the block is only emitted when
# the list is non-empty. See SecurityConfig.tokenCustomizer.)

# 3. For a user token (email/roles/uid), you'd complete the browser
#    authorization_code flow as an admin user and decode that JWT.

# 4. Flip flag off → restart → 'authorities' claim gone.
```

### Real-world claim naming trade-offs
| Choice | Pro | Con |
|---|---|---|
| Short names (`uid`, `roles`) | Tiny tokens, easy to read | Collision risk with future standard claims |
| Namespaced (`https://myapp.com/roles`) | Zero collision risk | Ugly, verbose, ~30 more bytes each |
| Standard OIDC where possible (`email`, `email_verified`) | Interop with OIDC-aware libraries | Only some fields have standard names |

We use short names because we control both sides. Namespaced is what auth0/okta do because they don't know downstream schemas.

### Trade-offs
| Claims ON | Claims OFF |
|---|---|
| Zero-hop authorization (roles are in the token) | Every request needs a userinfo call |
| Bigger tokens (~200 bytes more) | Small tokens |
| Token invalidation-on-role-change is impossible until expiry | Role change is instantly effective |
| PII (email, uid) flows through logs / caches / dashboards | Only opaque sub |

Real prod chooses per-service: high-throughput services want claims in the JWT; sensitive-data services keep tokens opaque and re-fetch.

### Interview line
"We hook the built-in `OAuth2TokenCustomizer` bean. Per grant type: user-tokens get `email`, `roles`, `uid` from the loaded `AppUser`; client-credentials tokens get their `authorities`. Every claim is behind a fine-grained flag — turn off `include-email` without changing code, useful for privacy compliance. The customizer runs INSIDE Spring's `NimbusJwtEncoder`, so the claims are signed as part of the JWT — no separate serialization path."

---

## Feature 10 — Prometheus metrics (Sept 2026)

### Motivation
"Was there a spike in failed logins at 3am?" is unanswerable from logs alone at scale — you'd have to grep every host, aggregate by minute, chart it manually. Prometheus turns app-level counters into time series. Grafana dashboards + AlertManager rules make abnormal patterns detectable within seconds.

### What we ship for free vs. what we add
```
FREE (once micrometer-registry-prometheus is on the classpath):
   http_server_requests_seconds       every URL, count+timing+error rate
   jvm_memory_used_bytes              heap + non-heap
   jvm_gc_pause_seconds
   jvm_threads_live_threads
   hikaricp_connections_active        DB pool state
   logback_events_total{level=ERROR}

CUSTOM (AuthMetrics bean):
   auth_tokens_issued_total{grant, client_id}
   auth_login_attempts_total{result}          SUCCESS / FAIL / LOCKED
   auth_lockouts_total
   auth_rate_limit_denials_total{endpoint}
   auth_key_rotations_total
```

### Property surface
```properties
features.metrics.enabled=true
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.prometheus.enabled=true
```

| Setting | Default | Purpose |
|---|---|---|
| `features.metrics.enabled` | `false` | Master switch. Off → `AuthMetrics.record*` are no-ops. Spring's default HTTP + JVM metrics still emit as long as the endpoint is on. |
| `management.endpoints.web.exposure.include` | `health` | Whitelist for `/actuator/*`. Adds `prometheus`. |
| `management.endpoint.prometheus.enabled` | `false` | Boot's Prometheus endpoint enable flag. |

### Tag policy (interview material)
| Tag | OK? | Why |
|---|---|---|
| `client_id` | yes | bounded (thousands max in prod) |
| `grant` | yes | 4 values total |
| `result` | yes | 3 values (SUCCESS/FAIL/LOCKED) |
| `endpoint` | yes | small enum |
| `username` | **NO** | infinite cardinality → Prometheus dies |
| `ip` | **NO** | v4 = 4B, v6 = 2^128 — same explosion |
| `user_agent` | **NO** | thousands of variants, high cardinality |

**Interview line:** "Cardinality is the trap. Every unique tag combination = one time series in Prometheus. Any tag with unbounded values will OOM your monitoring — even if the auth-server itself is fine."

### Files touched
```
CREATE  metrics/AuthMetrics.java                        5 counters, no-op when flag off

MODIFY  auth-server/pom.xml                             +micrometer-registry-prometheus
MODIFY  config/FeatureFlags.java                        +Metrics nested class
MODIFY  config/FeaturesConfig.java                      log metrics flag
MODIFY  application-jdbc.properties                     +features.metrics.* +management.*
MODIFY  application-inmemory.properties                 +features.metrics.enabled=false
MODIFY  config/SecurityConfig.java                      +metrics.tokenIssued() in tokenCustomizer
MODIFY  security/LockoutTracker.java                    +metrics.loginAttempt() + lockoutFired()
MODIFY  security/RateLimitFilter.java                   +metrics.rateLimitDenied()
MODIFY  security/RateLimitFilterRegistration.java       +AuthMetrics param on filter constructor
MODIFY  service/admin/KeyRotationService.java           +metrics.keyRotated()
```

### Endpoint access
`/actuator/prometheus` is under `/actuator/**` which the default chain already permits with no auth. In production, this endpoint should be behind either:
- **Basic auth** (dedicated `metrics-user`)
- **IP allow-list** (only Prometheus can reach it)
- **Network isolation** (separate management port)

Documented for future work; left public here for learning ease.

### Verification
```bash
# 1. Boot log shows metrics.enabled=true.

# 2. Baseline scrape — nothing custom yet:
curl -s http://localhost:8095/actuator/prometheus | grep "^auth_" | head -5

# 3. Trigger events:
for i in 1 2 3; do
  curl -s -u m2m-client:m2m-secret -d grant_type=client_credentials \
    http://localhost:8095/oauth2/token > /dev/null
done
for i in 1 2; do
  curl -s -c /tmp/c -d "username=nonexistent&password=x" \
    -d "_csrf=$(curl -s -c /tmp/c http://localhost:8095/login \
      | grep -oE 'name=\"_csrf\"[^>]*value=\"[^\"]+\"' \
      | head -1 | sed 's/.*value=\"\\([^\"]*\\)\".*/\\1/')" \
    http://localhost:8095/login > /dev/null
done

# 4. Scrape again:
curl -s http://localhost:8095/actuator/prometheus | grep "^auth_"
# Expect:
#   auth_login_attempts_total{result="FAIL"} 2.0
#   auth_tokens_issued_total{client_id="m2m-client",grant="client_credentials"} 3.0
```

### Interview line
"One dep — `micrometer-registry-prometheus` — brings the entire Spring HTTP + JVM story to `/actuator/prometheus` as time series. AuthMetrics is a thin façade wrapping Micrometer's `Counter` API; every counter lives behind the master flag so per-environment ops-toggling doesn't need a redeploy. Tags are chosen for bounded cardinality — client_id yes, username no. Downstream: Grafana per-client dashboards, AlertManager rules like `rate(auth_login_attempts_total{result='FAIL'}[5m]) > 1` for brute-force detection."

---

## Feature 8 — Consent screen (Sept 2026)

### Motivation
For third-party clients, the user must see "this app wants access to your email + profile" — the "Sign in with Google" pattern. First-party apps skip consent (the checkbox `requireAuthorizationConsent=false`); third-party apps set it to true and get an approval UI. Spring already stores grants in `oauth2_authorization_consent` and ships a default (plain white) consent page. This feature swaps in a Bootstrap-styled Thymeleaf version.

### What ships with Spring vs. what we add
```
Spring Authorization Server built-in:
   /oauth2/consent          default auto-generated page
   OAuth2AuthorizationConsentService  persists grants in DB
   Auto-skip if user already granted requested scopes
   Auto-suppress the page when requireAuthorizationConsent=false

We add:
   Thymeleaf template with matching Bootstrap navbar / cards
   Scope descriptions (openid → "Verify your identity", etc.)
   Previously-granted scopes pre-checked
   Feature flag so we can flip back to Spring's default
```

### Property surface
```properties
features.consent-page.enabled=true
```
Single flag. When off, `applyDefaultSecurity` uses Spring's own consent endpoint. When on, we register `/oauth2/consent` as the custom endpoint.

### Files touched
```
CREATE  controller/oauth/ConsentController.java     GET /oauth2/consent
CREATE  templates/oauth2/consent.html               Bootstrap approval UI

MODIFY  config/FeatureFlags.java                    +ConsentPage nested class
MODIFY  config/FeaturesConfig.java                  log consent-page flag
MODIFY  application-jdbc.properties                 +features.consent-page.enabled=true
MODIFY  application-inmemory.properties             +features.consent-page.enabled=false
MODIFY  config/SecurityConfig.java                  gate consentPage("/oauth2/consent") on flag
```

### How the flow uses this
```
1. Client redirects browser to /oauth2/authorize?scope=openid+profile+read&...
2. User not logged in → redirect to /login → user logs in → back to /oauth2/authorize
3. Spring evaluates client.requireAuthorizationConsent
   ├─ false → issue code immediately (first-party client behavior)
   └─ true  → redirect to consent page:
              flag OFF → Spring's default (plain white page)
              flag ON  → OUR /oauth2/consent (Bootstrap, scope descriptions,
                         previously-granted pre-checked)
4. User approves subset of scopes → form POSTs back to /oauth2/authorize with
   selected scope values → Spring writes to oauth2_authorization_consent, then
   redirects with ?code=xxx to the client's redirect_uri
```

### Verification (interactive)
Needs a browser because the whole point is a form the user clicks through.

```
1. Log in as admin, /admin/clients → edit demo-client → check "Require consent" → save.
2. Visit:
   http://localhost:8095/oauth2/authorize
     ?response_type=code
     &client_id=demo-client
     &redirect_uri=http://127.0.0.1:8097/login/oauth2/code/demo-client
     &scope=openid+profile+read
     &state=xyz
3. Log in as admin/password
4. Land on our custom /oauth2/consent — Bootstrap card, scope checkboxes,
   descriptions on each row.
5. Uncheck 'read', click Approve.
6. Redirect to 127.0.0.1:8097 with ?code=... (browser 404s because no client
   is running — this is expected; the auth-server did its job).
7. DB check:
   SELECT registered_client_id, principal_name, authorities
     FROM oauth2_authorization_consent;
   → one row per (client, user) with 'openid,profile' (no 'read' since we unchecked).
8. Same URL again → land on consent page with openid + profile PRE-CHECKED
   ('previously granted' badge visible).
```

### Trade-offs
| Consent ON | Consent OFF |
|---|---|
| User approves scopes explicitly | Silent trust — user never sees scopes |
| Adds one click to third-party sign-in | Fastest UX |
| Only third-party clients see it (per-client flag) | N/A |
| Grants stored → next login is faster | Nothing to store |

### Interview line
"Third-party OAuth clients get a scope-approval screen; first-party clients skip via `requireAuthorizationConsent=false`. Spring stores grants in `oauth2_authorization_consent` — the same table our V1 migration created back in Phase 3 — so subsequent logins for the same client+user skip the page unless new scopes are requested. We swap the default consent page for a Thymeleaf template that matches the admin UI, and pre-check previously-granted scopes so users only see what's new. Feature-flagged so a rollback is one config change."

---

## Feature 12 — Dynamic Client Registration (Sept 2026)

### Motivation
Manual client onboarding via the admin UI doesn't scale to CI/CD, Terraform, or fleet management. RFC 7591 defines a JSON REST endpoint (`POST /connect/register`) that programmatically creates clients. Terraform providers for Keycloak/Auth0/Okta all use this shape.

### What Spring provides
Spring Authorization Server 1.2.4 ships DCR support via the **OIDC** subpath (`.oidc(oidc -> oidc.clientRegistrationEndpoint(...))`). The endpoint URL is still `/connect/register` — the OIDC framing just adds ID-token-related metadata to the request/response.

We enable it (one line) and seed a bootstrap "registrar" client that has scope `client.create` — anyone with that client's credentials can call the endpoint.

### The bootstrap flow
```
Step 1 — get a token from the registrar client:
   curl -u registrar:registrar-secret \
        -d grant_type=client_credentials -d scope=client.create \
        http://localhost:8095/oauth2/token
   → { access_token: "eyJ..." }

Step 2 — register a new client:
   curl -X POST http://localhost:8095/connect/register \
        -H "Authorization: Bearer eyJ..." \
        -H "Content-Type: application/json" \
        -d '{
              "client_name": "my-new-app",
              "redirect_uris": ["https://myapp.com/callback"],
              "grant_types": ["authorization_code","refresh_token"],
              "response_types": ["code"],
              "scope": "openid profile"
            }'
   → 201 { client_id, client_secret (plaintext, shown ONCE),
           registration_access_token, registration_client_uri, ... }

Step 3 — RFC 7592 read/update/delete lifecycle:
   curl -H "Authorization: Bearer <registration_access_token>" \
        http://localhost:8095/connect/register/<new_client_id>
   → the full client metadata
```

### Property surface
```properties
features.dcr.enabled=true
```
Single toggle. Endpoint URL is fixed by Spring (`/connect/register`).

### V10 migration
Seeds `registrar` — `client_credentials` grant, secret `registrar-secret` (BCrypt-hashed), scopes `client.create,client.read`. See V10 file for the full INSERT.

### Files touched
```
CREATE  db/migration/V10__dcr_registrar_client.sql   seed the bootstrap client

MODIFY  config/FeatureFlags.java             +Dcr nested class
MODIFY  config/FeaturesConfig.java           log dcr.enabled
MODIFY  application-jdbc.properties          +features.dcr.enabled=true
MODIFY  application-inmemory.properties      +features.dcr.enabled=false
MODIFY  config/SecurityConfig.java           gate .oidc(.clientRegistrationEndpoint(...)) on flag
```

### Security note — the OIDC subpath trap
Spring Auth Server 1.2.x makes DCR available ONLY through `.oidc(oidc -> oidc.clientRegistrationEndpoint(...))`, not as a top-level RFC 7591 endpoint. Compilation fails if you use `.clientRegistrationEndpoint(...)` at the top-level configurer. Bare RFC 7591 support (without OIDC coupling) may arrive in a later Spring version — for now the OIDC path serves the same URL.

### What's intentionally deferred
- **Admin UI to mint short-lived initial access tokens** (currently: use the bootstrap `registrar` credentials).
- **Audit trail for DCR events** — Spring writes directly to `oauth2_registered_client`, bypassing our `ClientAdminService`. Wire a decorator around `RegisteredClientRepository` to call `AuditService.recordClient("CREATE", ..., viaDcr)`.
- **Rate limiting on `/connect/register`** — Feature 6's filter only covers `/login` + `/oauth2/token`. Extend the URL list if abuse is a concern.
- **PKCE enforcement from Feature 2** — DCR bypasses `ClientAdminService.validatePkce()`. Add a `RegisteredClientConfigurer` bean if the rule should cross-cut.

### Verification
```bash
# 1. Boot log: dcr.enabled=true + "FEATURE 12: DCR endpoint enabled".

# 2. Bootstrap token:
TOK=$(curl -s -u registrar:registrar-secret \
        -d grant_type=client_credentials -d scope=client.create \
        http://localhost:8095/oauth2/token | jq -r .access_token)

# 3. Register:
curl -s -X POST http://localhost:8095/connect/register \
     -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
     -d '{"client_name":"dcr-test","redirect_uris":["https://x.com/cb"],
          "grant_types":["authorization_code"],"response_types":["code"],
          "scope":"openid profile"}' | jq

# 4. DB:
docker exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD -N -e \
  "USE authdb_jdbc; SELECT client_id, client_name FROM oauth2_registered_client
   WHERE client_name='dcr-test';"

# 5. Flip flag off, restart → POST /connect/register → 401 or 404 depending on chain.
```

### Interview line
"Spring Auth Server ships RFC 7591 out of the box through the OIDC subpath — flip a single configurer, seed a bootstrap client with scope `client.create`, and any pipeline can `terraform apply` new OAuth clients over standards-based JSON. We deferred audit/rate-limit for the DCR URL; both are documented as follow-ups. The bootstrap-client-with-mgmt-scope pattern is what Keycloak, Auth0, and Okta all use — you're not inventing a protocol, you're wiring the standard endpoint Spring provides."

---

## Feature 14 — REST API mirror (Sept 2026)

### Motivation
Everything the browser admin does needs a machine-driven equivalent. Terraform providers, K8s operators, CI/CD pipelines, and SPA admin frontends all need JSON APIs — not scraped HTML. Feature 14 delivers `/api/v1/admin/**` as full CRUD JSON, using the same services + audit trail the browser UI does.

### The endpoint map
```
GET    /api/v1/admin/clients             list                 scope=admin.read
GET    /api/v1/admin/clients/{id}        get one              scope=admin.read
POST   /api/v1/admin/clients             create → 201         scope=admin.write
PUT    /api/v1/admin/clients/{id}        update               scope=admin.write
DELETE /api/v1/admin/clients/{id}        delete → 204         scope=admin.write

GET    /api/v1/admin/users               list                 scope=admin.read
GET    /api/v1/admin/users/{id}          get one              scope=admin.read
POST   /api/v1/admin/users               create → 201         scope=admin.write
PUT    /api/v1/admin/users/{id}          update               scope=admin.write
DELETE /api/v1/admin/users/{id}          delete → 204         scope=admin.write
POST   /api/v1/admin/users/{id}/unlock   unlock account       scope=admin.unlock

GET    /api/v1/admin/keys                list                 scope=admin.read
POST   /api/v1/admin/keys/rotate         rotate → new PRIMARY scope=admin.write
POST   /api/v1/admin/keys/{kid}/retire   retire SECONDARY     scope=admin.write

GET    /api/v1/admin/audit?page=N        paged audit list     scope=admin.read
```

### Auth model — JWT bearer with 3 scopes
Caller acquires a token from `/oauth2/token` using the seeded `api-admin` bootstrap client (V11 migration), then sends `Authorization: Bearer <jwt>`:
```
admin.read     GET everywhere
admin.write    POST/PUT/DELETE clients/users/keys
admin.unlock   /users/{id}/unlock  (finer granularity — security-sensitive)
```
Enforced via method security (`@PreAuthorize("hasAuthority('SCOPE_admin.write')")`). Spring's `oauth2ResourceServer(jwt)` maps JWT `scope` claim values to `SCOPE_*` authorities automatically.

### Filter chain layout (final)
```
@Order(-1)  /api/v1/**       JWT bearer, stateless, CSRF off      (Feature 14)
@Order(0)   /admin/**        session + formLogin, CSRF on         (Phase 6)
@Order(1)   OAuth endpoints  Spring's default                     (Phase 0)
@Order(2)   everything else  permitAll on /login /webjars /error  (Phase 0)
```
Order matters: `-1 < 0`, so the API chain takes `/api/v1/**` requests first. Its `securityMatcher` limits it to that prefix, so other requests fall through.

### Audit trail differentiation (free win)
`ClientAdminService.save()` reads `SecurityContextHolder.getContext().getAuthentication().getName()` for the audit actor. When called via:
- **Browser** → actor = `admin` (session's principal name)
- **REST API** → actor = `api-admin` (JWT's `sub` claim)

Zero code change needed — the audit log naturally separates human vs. machine changes.

### Error format — RFC 7807
```json
Content-Type: application/problem+json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "client id=xyz",
  "instance": "/api/v1/admin/clients/xyz"
}
```
Standard machine-readable errors. Handled by `ApiExceptionAdvice` (`@RestControllerAdvice`).

### Files delivered
```
CREATE  db/migration/V11__api_admin_client.sql       seed api-admin bootstrap client

CREATE  api/v1/dto/ClientRequest.java                POST/PUT body
CREATE  api/v1/dto/ClientResponse.java               JSON response
CREATE  api/v1/dto/UserRequest.java
CREATE  api/v1/dto/UserResponse.java                 no password ever
CREATE  api/v1/dto/KeyResponse.java                  no private_key ever
CREATE  api/v1/dto/AuditResponse.java
CREATE  api/v1/dto/ProblemDetail.java                RFC 7807 body

CREATE  api/v1/ClientRestController.java             /api/v1/admin/clients
CREATE  api/v1/UserRestController.java               /api/v1/admin/users + /unlock
CREATE  api/v1/KeyRestController.java                /api/v1/admin/keys + /rotate + /retire
CREATE  api/v1/AuditRestController.java              /api/v1/admin/audit
CREATE  api/v1/ApiExceptionAdvice.java               @RestControllerAdvice → RFC 7807

MODIFY  config/FeatureFlags.java                     +RestApi nested class
MODIFY  config/FeaturesConfig.java                   log rest-api flag
MODIFY  application-jdbc.properties                  +features.rest-api.enabled=true
MODIFY  application-inmemory.properties              +features.rest-api.enabled=false
MODIFY  service/admin/ClientAdminService.java        +findByClientId() helper
MODIFY  config/JdbcSecurityConfig.java               +@EnableMethodSecurity, +@Order(-1) chain
```

### Verification
```bash
# 1. Bootstrap token
TOK=$(curl -s -u api-admin:api-admin-secret \
        -d grant_type=client_credentials \
        -d "scope=admin.read admin.write" \
        http://localhost:8095/oauth2/token | jq -r .access_token)

# 2. List clients
curl -s -H "Authorization: Bearer $TOK" http://localhost:8095/api/v1/admin/clients | jq

# 3. Create
curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
     -d '{"clientId":"rest-test","clientName":"Rest Test","clientSecret":"restsec",
          "grantTypes":["client_credentials"],"authMethods":["client_secret_basic"],
          "scopes":["read"]}' \
     http://localhost:8095/api/v1/admin/clients | jq

# 4. 401 without token
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8095/api/v1/admin/clients
# → 401

# 5. Wrong scope → 403 (get read-only token, try POST)
TOK_R=$(curl -s -u api-admin:api-admin-secret \
          -d grant_type=client_credentials -d scope=admin.read \
          http://localhost:8095/oauth2/token | jq -r .access_token)
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
     -H "Authorization: Bearer $TOK_R" -H "Content-Type: application/json" \
     -d '{"clientId":"x","clientName":"x","clientSecret":"y"}' \
     http://localhost:8095/api/v1/admin/clients
# → 403 with RFC 7807 body
```

### Interview line
"Every browser CRUD has a JSON twin under `/api/v1/admin/**`. Three scopes — `admin.read`, `admin.write`, `admin.unlock` — enforced at the method level via `@PreAuthorize`. Auth is JWT bearer through our own `/oauth2/token` endpoint using a seeded `api-admin` client; scopes on the JWT map automatically to `SCOPE_*` authorities. Errors follow RFC 7807 `application/problem+json`. Controllers are thin wrappers over `ClientAdminService`/`UserAdminService`/`KeyRotationService` — same code path as the browser, same audit trail (actor field auto-distinguishes `admin` vs `api-admin`). Unlocks Terraform providers, K8s operators, and SPA admin frontends without touching the Thymeleaf layer."

---

## Feature status — 11 shipped, roadmap complete

- Refresh token rotation ✔ (Feature 1)
- PKCE enforcement    ✔ (Feature 2)
- Key rotation        ✔ (Feature 3)
- Audit log           ✔ (Feature 4)
- Account lockout     ✔ (Feature 5)
- Rate limiting       ✔ (Feature 6)
- Custom JWT claims   ✔ (Feature 7)
- Consent screen      ✔ (Feature 8)
- Prometheus metrics  ✔ (Feature 10)
- Dynamic Client Registration ✔ (Feature 12)
- REST API mirror     ✔ (Feature 14)

## Feature 11 — KMS-backed signing keys (Sept 2026)

### Motivation
Feature 3 stored the RSA private key as a PEM string in MySQL. Anyone with `SELECT` on the `signing_key` table — DB backup access, DBA with read-only creds, compromised replica — can forge tokens for any client or user. The blast radius of a DB compromise = complete auth-server takeover.

KMS-backed signing fixes this: the private key lives inside HashiCorp Vault's transit engine, never leaves it. The auth-server has ZERO copies of the private key. To forge a token, an attacker needs Vault IAM (much smaller attack surface than the primary database).

### Architecture — the SigningKeyStore abstraction
```
           SigningKeyStore  (interface)
                  │
        ┌─────────┴──────────┐
        │                    │
 JpaSigningKeyStore    VaultSigningKeyStore
   Feature 3 behaviour:  Feature 11 (this):
     PEM in DB             Private key in Vault
     local Signature       remote /v1/transit/sign call
     JVM does crypto       Vault does crypto
```

Backend picked by `features.kms-keys.backend` (jpa | vault). Same interface; wildly different security posture.

### The critical trap: Spring's NimbusJwtEncoder needs the private key
Spring auto-configures a `NimbusJwtEncoder` from the `JWKSource<SecurityContext>` bean. Nimbus expects `RSAPrivateKey` in the `RSAKey` — impossible with Vault. We solve this by registering a `RemoteSigningJwtEncoder` that:
1. Base64URL-encodes the JOSE header + payload ourselves
2. Sends the signing input to Vault via `SigningKeyStore.sign()`
3. Base64URL-encodes the returned signature
4. Concatenates `header.payload.signature` into the final JWT

Registered only when `backend=vault` via `@ConditionalOnProperty`. On JPA backend Spring's default encoder continues.

### Property surface
```properties
features.kms-keys.enabled=true
features.kms-keys.backend=vault              # jpa | vault
features.kms-keys.vault.uri=http://localhost:8200
features.kms-keys.vault.token=root           # dev-mode default
features.kms-keys.vault.transit-path=transit
features.kms-keys.vault.key-name=auth-server
```

### Infrastructure (docker-compose)
```yaml
vault-dev:
  image: hashicorp/vault:1.15
  ports: ["8200:8200"]
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: root
  cap_add: [IPC_LOCK]
```
Dev mode auto-unseals, uses in-memory storage, root token is `root`. **Never use dev mode in prod.**

### Bootstrap
`VaultSigningKeyStore` constructor is idempotent:
1. Attempts to mount transit engine (`sys/mounts/transit`) — swallows the "already mounted" error.
2. Reads `transit/keys/auth-server` — if absent, creates it as `rsa-2048`.

Works on:
- Fresh Vault → mounts + creates key
- Existing Vault → skips both, uses existing key
- Vault restart mid-run → connection resets, fails gracefully next call

### Vault's transit sign endpoint details
```
POST /v1/transit/sign/auth-server/sha2-256
Body: {
  "input": "<base64-of-signing-input>",
  "signature_algorithm": "pkcs1v15"     ← critical for JWS RS256 match
}
Response: {
  "signature": "vault:v1:<base64-signature>"
}
```
Vault's default RSA sig is PSS (matches JWS PS256). We force `pkcs1v15` because Spring's JWS RS256 uses PKCS#1 v1.5 padding. If we didn't specify, tokens wouldn't verify against RS256-only clients.

### kid convention
JPA:   `kid = <UUID>`  (Feature 3)
Vault: `kid = vault:auth-server:v1`  (embeds Vault version)

Downstream verifiers cache the JWKS by kid; the version being in the kid means rotation → new kid → new version emitted.

### Files delivered
```
CREATE  crypto/SigningKeyStore.java             interface
CREATE  crypto/JpaSigningKeyStore.java          refactor of Feature 3 crypto
CREATE  crypto/VaultSigningKeyStore.java        Vault transit backend
CREATE  crypto/RemoteSigningJwtEncoder.java     JOSE-encoding JwtEncoder
CREATE  crypto/VaultConfig.java                 VaultTemplate bean (conditional)

MODIFY  docker-compose.yml                      +vault-dev service
MODIFY  auth-server/pom.xml                     +spring-vault-core 3.1.1
MODIFY  config/FeatureFlags.java                +KmsKeys nested class (with Backend enum)
MODIFY  config/FeaturesConfig.java              log kms-keys.enabled + backend
MODIFY  application-jdbc.properties             +features.kms-keys.*
MODIFY  application-inmemory.properties         +features.kms-keys.enabled=false
MODIFY  config/JdbcSecurityConfig.java          SigningKeyStore bean picks backend;
                                                jwkSource delegates to store;
                                                conditional RemoteSigningJwtEncoder
```

### Verification (backend=vault)
```bash
# 1. Start Vault
docker compose up -d vault-dev

# 2. Enable backend
#    features.kms-keys.enabled=true
#    features.kms-keys.backend=vault

# 3. Boot auth-server. Expected log:
#    Vault: enabled transit engine at transit
#    Vault: created transit key auth-server (type=rsa-2048)
#    FEATURE 11: SigningKeyStore backend = VAULT
#    FEATURE 11: JwtEncoder = RemoteSigningJwtEncoder (Vault-backed)

# 4. Get a token
TOK=$(curl -s -u m2m-client:m2m-secret -d grant_type=client_credentials \
      -d scope=read http://localhost:8095/oauth2/token | jq -r .access_token)

# 5. Decode header — kid should be "vault:auth-server:v1"
echo $TOK | cut -d. -f1 | base64 -d | jq .kid
# → "vault:auth-server:v1"

# 6. JWKS returns Vault-tagged key
curl -s http://localhost:8095/oauth2/jwks | jq
# → keys[0].kid = "vault:auth-server:v1"

# 7. Rotate (via REST API from Feature 14):
TOK_ADM=$(curl -s -u api-admin:api-admin-secret -d grant_type=client_credentials \
          -d "scope=admin.write" http://localhost:8095/oauth2/token | jq -r .access_token)
curl -s -X POST -H "Authorization: Bearer $TOK_ADM" \
     http://localhost:8095/api/v1/admin/keys/rotate
# Note: /api/v1/admin/keys/rotate delegates to KeyRotationService.rotate() which
# uses SigningKeyRepository directly — for Vault backend, rotation needs to go
# through SigningKeyStore. This is documented as a follow-up: refactor
# KeyRotationService to use SigningKeyStore instead of SigningKeyRepository.
```

### Trade-offs
| Vault backend | JPA backend |
|---|---|
| Private key never in JVM | Private key in DB (readable by DBA / backup) |
| Sign = network call (~5-20ms) | Sign = local CPU (~1ms) |
| Needs Vault infrastructure | Zero external deps |
| Real prod pattern (AWS KMS, GCP KMS, Vault) | Fine for dev / low-value environments |
| Vault outage = auth-server can't issue tokens | Only DB outage matters |

### Deferred (documented follow-ups)
- **KeyRotationService.rotate() still writes directly to SigningKeyRepository.**
  For full Vault support, the admin UI rotate button + REST endpoint should call
  `SigningKeyStore.rotate()` instead. One-hour refactor.
- **AppRole auth for prod Vault.** Currently uses root token (dev mode only). Prod:
  swap `new TokenAuthentication(v.getToken())` for `new AppRoleAuthentication(...)`.
- **Vault seal handling.** Real Vault seals on restart; auto-unseal via KMS is the
  standard pattern. Out of scope here.

### Interview line
"Private key lives in HashiCorp Vault's transit engine. Auth-server never has it — every JWT sign is a `POST /v1/transit/sign/auth-server` call; Vault does the RSA math, we build the JWS envelope. Introduces a `SigningKeyStore` interface so JPA (dev) and Vault (prod) are one config flag apart. Trap I hit: Spring's default `NimbusJwtEncoder` requires the private key locally, so I registered a `RemoteSigningJwtEncoder` that base64URL-encodes the header + payload, sends them to Vault for signing, and concatenates the result. Real prod backends — AWS KMS, GCP KMS — are the same abstraction, different `SigningKeyStore` implementation."

---

## Feature roadmap status

- Refresh token rotation ✔ (Feature 1)
- PKCE enforcement    ✔ (Feature 2)
- Key rotation        ✔ (Feature 3)
- Audit log           ✔ (Feature 4)
- Account lockout     ✔ (Feature 5)
- Rate limiting       ✔ (Feature 6)
- Custom JWT claims   ✔ (Feature 7)
- Consent screen      ✔ (Feature 8)
- Prometheus metrics  ✔ (Feature 10)
- KMS-backed keys     ✔ (Feature 11)
- Dynamic Client Registration ✔ (Feature 12)
- REST API mirror     ✔ (Feature 14)

## Feature 9 — Zipkin tracing (Sept 2026)

### Motivation
Logs tell you WHAT happened; traces tell you WHERE the time went AND how requests flowed across services. Every request gets a `trace_id` that's carried through all downstream calls (JDBC, Vault, resource-server). Zipkin visualises the timing as nested spans — one look and you see "the token endpoint spent 42ms in Vault sign, 15ms in DB, 63ms elsewhere".

### The Micrometer Tracing choice
Spring Boot 3.2 gives two implementations:
- **Brave bridge** → Zipkin exporter (what we use)
- **OpenTelemetry bridge** → OTLP exporter (Jaeger/Tempo/DataDog)

Brave + Zipkin is the simpler path when the sink IS Zipkin. Two deps, one property flip, and every HTTP request + JDBC statement + WebClient call is a span automatically.

### What's traced for free
```
✔ Every incoming HTTP request                   http.server.requests
✔ Every JDBC statement (via Micrometer JDBC)    jdbc.query
✔ Every outgoing WebClient / RestTemplate       http.client
✔ Every scheduled task
✔ trace_id + span_id in MDC → log lines carry both
```

Plus one custom span we added: `vault.sign` wraps `VaultSigningKeyStore.sign()` so the Vault latency is separately visible.

### Log pattern includes trace context
Updated `application.properties`:
```
logging.pattern.console={"time":"...","trace":"%X{traceId:-}","span":"%X{spanId:-}","message":"%m"}
```
`%X{traceId:-}` reads MDC; the `:-` gives an empty default when tracing is off. Same trace_id in your log line AND in Zipkin UI — grep across services, cross-reference to trace timeline.

### Property surface
```properties
features.tracing.enabled=true
management.tracing.enabled=${features.tracing.enabled}
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```
The `${...}` binding gives us fail-closed: feature flag off → Spring's tracing autoconfig disabled → no overhead, no spans reported.

### Sampling probability
- **1.0** — trace every request. Fine for dev (~2 KB of network per span batch).
- **0.1** — sample 10%. Prod default. Still enough volume for outlier debugging.
- **0.01** — high-traffic prod (10k+ RPS). Statistical, not exhaustive.

We use 1.0 for learning.

### The custom Vault span
```java
Observation.createNotStarted("vault.sign", observationRegistry)
    .lowCardinalityKeyValue("key.name", keyName)
    .observe(() -> {
        // ... Vault HTTP call ...
    });
```
`lowCardinalityKeyValue` adds a tag that Zipkin/Prometheus can group by. `key.name` bounded to a handful of key aliases — perfect. Never use `traceId` or user-controlled input here (cardinality explosion).

### Files touched
```
MODIFY  auth-server/pom.xml                       +micrometer-tracing-bridge-brave
                                                   +zipkin-reporter-brave
MODIFY  config/FeatureFlags.java                  +Tracing nested class
MODIFY  config/FeaturesConfig.java                 log tracing.enabled
MODIFY  application-jdbc.properties                +features.tracing.*
                                                   +management.tracing.*
                                                   +management.zipkin.tracing.endpoint
MODIFY  application-inmemory.properties           tracing off
MODIFY  application.properties                    log pattern with %X{traceId} %X{spanId}
MODIFY  crypto/VaultSigningKeyStore.java          +vault.sign Observation span
MODIFY  config/JdbcSecurityConfig.java             inject ObservationRegistry into store
```

### Verification
```bash
# 1. Start Zipkin (already in compose)
docker compose up -d zipkin

# 2. Boot auth-server. Expected: no APPLICATION FAILED, log lines carry
#    "trace":"<64-char-hex>" and "span":"<16-char-hex>".

# 3. Fire a token request
curl -s -u m2m-client:m2m-secret -d grant_type=client_credentials \
     http://localhost:8095/oauth2/token > /dev/null

# 4. Open Zipkin UI: http://localhost:9411
#    → Click "Run Query" → see a trace for POST /oauth2/token
#    → Click it → inline waterfall: JDBC calls, vault.sign, JWT encode

# 5. Tail auth-server logs — traceId appears in every log line during a request:
#    "trace":"abc123...","span":"def456...","message":"Issuing token..."
```

### Trade-offs
| Tracing ON | Tracing OFF |
|---|---|
| Per-request latency breakdown available | Only aggregate p99 from metrics |
| ~2-5% CPU overhead at sampling=1.0 | Zero overhead |
| Bandwidth to Zipkin (~1-2 KB / batched span export) | Nothing |
| Requires Zipkin infrastructure | Nothing |
| Requires log-forwarding to see trace_id benefit | Just plain logs |

### Interview line
"Brave bridge → Zipkin. Every HTTP + JDBC call is instrumented for free by Spring Boot autoconfig; I added one custom `Observation` around the Vault sign call so latency for our KMS integration is visible as its own span. Log pattern includes `%X{traceId}` so grep-by-trace-id ties log lines to Zipkin's timeline view. Master switch is bound via `management.tracing.enabled=${features.tracing.enabled}` — one flag flip turns off both the feature and Spring's autoconfig."

---

## Feature roadmap status — 13 shipped

- Refresh token rotation ✔ (Feature 1)
- PKCE enforcement    ✔ (Feature 2)
- Key rotation        ✔ (Feature 3)
- Audit log           ✔ (Feature 4)
- Account lockout     ✔ (Feature 5)
- Rate limiting       ✔ (Feature 6)
- Custom JWT claims   ✔ (Feature 7)
- Consent screen      ✔ (Feature 8)
- Zipkin tracing      ✔ (Feature 9)
- Prometheus metrics  ✔ (Feature 10)
- KMS-backed keys     ✔ (Feature 11)
- Dynamic Client Registration ✔ (Feature 12)
- REST API mirror     ✔ (Feature 14)

Still queued (deferred):
- Feature 13  Multi-tenancy (realms)
- Custom JWT claims (roles, email, tenant)
- Consent screen (RFC 6749)
- Zipkin / OpenTelemetry tracing
- Prometheus metrics
- KMS-backed signing keys
- Dynamic Client Registration (RFC 7591)
- Multi-tenancy (realms)
- REST API mirror of the admin UI
