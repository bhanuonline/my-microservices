package com.example.apigateway.bodylogging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based redaction. Pre-compiled patterns for perf (no per-request compilation).
 *
 * Two layers:
 *   1. Field-name patterns  → "password":"..."       → "password":"***REDACTED***"
 *   2. Value patterns       → 4111111111111111       → ***REDACTED***
 *
 * Trade-off: regex on JSON is imperfect (escaped quotes, nested strings), but
 * fast + schema-free. For strict correctness use a JSON parser (extension parked
 * in docs).
 */
@Component
@EnableConfigurationProperties(BodyLoggingProperties.class)
@ConditionalOnProperty(prefix = "gateway.body-logging", name = "enabled", havingValue = "true")
public class BodyRedactor {

    private final List<Pattern> fieldPatterns;
    private final List<Pattern> valuePatterns;
    private final String replacement;

    public BodyRedactor(BodyLoggingProperties props) {
        this.replacement = props.getRedact().getReplacement();
        this.fieldPatterns = props.getRedact().getFieldNames().stream()
                .map(f -> Pattern.compile(
                        "\"(?i:" + Pattern.quote(f) + ")\"\\s*:\\s*\"([^\"]*)\"",
                        Pattern.MULTILINE))
                .toList();
        this.valuePatterns = props.getRedact().getPatterns().stream()
                .map(Pattern::compile)
                .toList();
    }

    public String redact(String body) {
        if (body == null || body.isEmpty()) return body;
        String out = body;

        // Layer 1: replace VALUES of sensitive field names, preserving the key
        for (Pattern p : fieldPatterns) {
            Matcher m = p.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String full = m.group();
                String value = m.group(1);
                String replaced = full.replace("\"" + value + "\"", "\"" + replacement + "\"");
                m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
            }
            m.appendTail(sb);
            out = sb.toString();
        }

        // Layer 2: value-pattern masking anywhere in the body
        for (Pattern p : valuePatterns) {
            out = p.matcher(out).replaceAll(Matcher.quoteReplacement(replacement));
        }
        return out;
    }
}
