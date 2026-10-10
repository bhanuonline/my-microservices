package com.example.apigateway.bodylogging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.body-logging")
public class BodyLoggingProperties {

    private boolean enabled = false;

    /** 0.0 – 1.0. Fraction of requests to log. 1.0 = every request. */
    private double sampleRate = 1.0;

    /** Truncate bodies larger than this many bytes. */
    private int maxBodyBytes = 8 * 1024;

    /** Paths where the filter is skipped entirely (no buffering, no logging). */
    private List<String> excludedPaths = List.of("/actuator/**", "/fallback/**");

    /** Only bodies with these Content-Types are decoded as text; others logged as binary marker. */
    private List<String> loggableContentTypes = List.of(
            "application/json",
            "application/xml",
            "text/plain",
            "text/html",
            "application/x-www-form-urlencoded"
    );

    private Redact redact = new Redact();
    private ResponseLogging response = new ResponseLogging();

    public static class Redact {
        /** JSON field names whose values are replaced. Case-insensitive. */
        private List<String> fieldNames = List.of(
                "password", "token", "secret", "apiKey",
                "authorization", "ssn", "cvv", "creditCard"
        );
        /** Regex patterns matched anywhere in the body — masked with replacement. */
        private List<String> patterns = List.of(
                "\\d{16}",                          // 16-digit numbers (cards)
                "\\d{3}-\\d{2}-\\d{4}",             // US SSN
                "sk_live_[a-zA-Z0-9]+"              // our API keys
        );
        private String replacement = "***REDACTED***";

        public List<String> getFieldNames() { return fieldNames; }
        public void setFieldNames(List<String> fieldNames) { this.fieldNames = fieldNames; }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }

        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
    }

    public static class ResponseLogging {
        private boolean enabled = true;
        /**
         * Empty = log ALL statuses. Non-empty = log ONLY when status starts with
         * one of these prefixes (e.g. "4xx", "5xx" for errors only).
         */
        private List<String> statusClasses = List.of();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getStatusClasses() { return statusClasses; }
        public void setStatusClasses(List<String> statusClasses) { this.statusClasses = statusClasses; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getSampleRate() { return sampleRate; }
    public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }

    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }

    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }

    public List<String> getLoggableContentTypes() { return loggableContentTypes; }
    public void setLoggableContentTypes(List<String> loggableContentTypes) { this.loggableContentTypes = loggableContentTypes; }

    public Redact getRedact() { return redact; }
    public void setRedact(Redact redact) { this.redact = redact; }

    public ResponseLogging getResponse() { return response; }
    public void setResponse(ResponseLogging response) { this.response = response; }
}
