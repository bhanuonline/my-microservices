package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;

/**
 * Runtime-refreshable via POST /actuator/refresh.
 *
 * Spring wraps this bean in a scope proxy — every getter call resolves to the
 * currently-active instance. When /actuator/refresh fires:
 *   1. Environment re-reads property sources (yaml, env, etc.)
 *   2. Existing instance is destroyed
 *   3. Next getter call creates a fresh instance with new Environment values
 *
 * Refreshable at runtime:
 *   ✓ keyTtl / lockTtl (applied to NEW writes; existing Redis entries keep original TTL)
 *   ✓ stripHeaders / streamingContentTypes / skipStreamingContent
 *   ✓ failOpenOnStoreError / requireHeader / verifyFingerprint
 *   ✓ headerName / fingerprintHeader
 *   ✓ enabled (works as a runtime kill-switch — see IdempotencyKeyGatewayFilterFactory)
 *
 * NOT refreshable (bean-creation-time only):
 *   ✗ @ConditionalOnProperty(...enabled=true) still gates BEAN CREATION at boot.
 *     If you boot with enabled=false, the filter bean does not exist; refresh won't
 *     resurrect it. Runtime toggle only works when the bean was created at boot.
 */
@RefreshScope
@ConfigurationProperties(prefix = "gateway.idempotency")
public class IdempotencyProperties {

    private boolean enabled = false;
    private String headerName = "Idempotency-Key";
    private List<HttpMethod> methods = List.of(HttpMethod.POST, HttpMethod.PATCH);
    private Duration keyTtl = Duration.ofHours(24);
    private Duration lockTtl = Duration.ofSeconds(30);
    private boolean requireHeader = false;
    private boolean deriveFromFingerprint = true;
    private boolean verifyFingerprint = true;
    private String fingerprintHeader = "X-Request-Fingerprint";

    /**
     * Response headers to strip before caching. Prevents replaying auth cookies
     * / bearer tokens to the wrong client. Case-insensitive.
     */
    private List<String> stripHeaders = List.of(
            "Set-Cookie",
            "Authorization",
            "Date"
    );

    /**
     * When true, responses whose Content-Type matches streamingContentTypes are
     * NOT buffered/cached — request is forwarded but its response passes through
     * unchanged. Prevents unbounded memory buffering of SSE / chunked downloads.
     */
    private boolean skipStreamingContent = true;
    private List<String> streamingContentTypes = List.of(
            "text/event-stream",
            "application/octet-stream",
            "application/x-ndjson"
    );

    /**
     * When true, Redis errors during lookup/lock/save bypass the filter — the
     * request proceeds as if Idempotency was disabled for that call. Trades
     * strict correctness for availability during Redis outages.
     */
    private boolean failOpenOnStoreError = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public List<HttpMethod> getMethods() { return methods; }
    public void setMethods(List<HttpMethod> methods) { this.methods = methods; }

    public Duration getKeyTtl() { return keyTtl; }
    public void setKeyTtl(Duration keyTtl) { this.keyTtl = keyTtl; }

    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration lockTtl) { this.lockTtl = lockTtl; }

    public boolean isRequireHeader() { return requireHeader; }
    public void setRequireHeader(boolean requireHeader) { this.requireHeader = requireHeader; }

    public boolean isDeriveFromFingerprint() { return deriveFromFingerprint; }
    public void setDeriveFromFingerprint(boolean deriveFromFingerprint) { this.deriveFromFingerprint = deriveFromFingerprint; }

    public boolean isVerifyFingerprint() { return verifyFingerprint; }
    public void setVerifyFingerprint(boolean verifyFingerprint) { this.verifyFingerprint = verifyFingerprint; }

    public String getFingerprintHeader() { return fingerprintHeader; }
    public void setFingerprintHeader(String fingerprintHeader) { this.fingerprintHeader = fingerprintHeader; }

    public List<String> getStripHeaders() { return stripHeaders; }
    public void setStripHeaders(List<String> stripHeaders) { this.stripHeaders = stripHeaders; }

    public boolean isSkipStreamingContent() { return skipStreamingContent; }
    public void setSkipStreamingContent(boolean skipStreamingContent) { this.skipStreamingContent = skipStreamingContent; }

    public List<String> getStreamingContentTypes() { return streamingContentTypes; }
    public void setStreamingContentTypes(List<String> streamingContentTypes) { this.streamingContentTypes = streamingContentTypes; }

    public boolean isFailOpenOnStoreError() { return failOpenOnStoreError; }
    public void setFailOpenOnStoreError(boolean failOpenOnStoreError) { this.failOpenOnStoreError = failOpenOnStoreError; }
}
