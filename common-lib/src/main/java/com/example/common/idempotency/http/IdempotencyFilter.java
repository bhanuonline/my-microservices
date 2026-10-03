package com.example.common.idempotency.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP idempotency filter — honours the `Idempotency-Key` header on configured
 * write endpoints.
 *
 * Behaviour:
 *   - First call with key K: forwards to the handler, caches (status, body) keyed by (K, endpoint).
 *   - Repeat call with same K and same body hash: returns the cached response, handler never runs.
 *   - Repeat call with same K but different body hash: 422 Unprocessable Entity.
 *   - Key missing or endpoint not whitelisted: pass-through, no state change.
 *
 * See the Stripe reference: https://stripe.com/docs/api/idempotent_requests
 *
 * This filter deliberately only caches 2xx + 4xx responses. 5xx responses are NOT
 * cached — a client that retries after a 5xx must be allowed to try again.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyRecordRepository repo;
    private final IdempotencyProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdempotencyFilter(IdempotencyRecordRepository repo, IdempotencyProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String key = req.getHeader(HEADER);
        String endpoint = req.getMethod() + " " + req.getRequestURI();

        if (key == null || key.isBlank()
                || !WRITE_METHODS.contains(req.getMethod())
                || !props.getEndpoints().contains(endpoint)) {
            chain.doFilter(req, res);
            return;
        }

        // Read + cache the body so the downstream handler can still read it.
        byte[] body = StreamUtils.copyToByteArray(req.getInputStream());
        String bodyHash = sha256Hex(body);
        String principal = currentPrincipal();

        Optional<IdempotencyRecord> existing = repo.findByKeyAndEndpoint(key, endpoint);
        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (!rec.getRequestHash().equals(bodyHash)) {
                writeJson(res, 422, Map.of(
                        "error", "idempotency_key_reused",
                        "message", "Idempotency-Key reused with a different request body"
                ));
                return;
            }
            log.info("idempotency replay key={} endpoint={} status={}", key, endpoint, rec.getStatusCode());
            res.setStatus(rec.getStatusCode());
            if (rec.getContentType() != null) res.setContentType(rec.getContentType());
            res.setHeader(HEADER, key);
            res.setHeader("Idempotency-Replay", "true");
            if (rec.getResponseBody() != null) {
                res.getWriter().write(rec.getResponseBody());
            }
            return;
        }

        // First time: run the handler against a cached-body request + wrap response.
        CachedBodyHttpServletRequest wrappedReq = new CachedBodyHttpServletRequest(req, body);
        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);
        chain.doFilter(wrappedReq, wrappedRes);

        int status = wrappedRes.getStatus();
        boolean cacheable = status < 500;
        if (cacheable) {
            byte[] responseBytes = wrappedRes.getContentAsByteArray();
            String responseBody = responseBytes.length <= props.getMaxBodyBytes()
                    ? new String(responseBytes, StandardCharsets.UTF_8)
                    : null;  // skip caching bodies larger than the cap
            try {
                repo.save(new IdempotencyRecord(
                        key, endpoint, principal, bodyHash,
                        status, wrappedRes.getContentType(), responseBody,
                        Instant.now(), Instant.now().plus(props.getTtl())
                ));
            } catch (DataIntegrityViolationException race) {
                // Two concurrent first-time calls with the same key. One wins the write;
                // we swallow the loser's duplicate-insert error. The response was already
                // computed and streamed to this caller, so no further action needed.
                log.debug("idempotency race on key={} endpoint={} — insert lost, cached by peer", key, endpoint);
            }
            res.setHeader(HEADER, key);
        }
        wrappedRes.copyBodyToResponse();
    }

    private static String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "anonymous";
    }

    private static String sha256Hex(byte[] in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(in));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void writeJson(HttpServletResponse res, int status, Object body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getWriter(), body);
    }

    /** Minimal wrapper so the downstream handler can re-read the body we already consumed. */
    private static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyHttpServletRequest(HttpServletRequest req, byte[] body) {
            super(req);
            this.body = body;
        }
        @Override public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener l) {}
            };
        }
        @Override public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
