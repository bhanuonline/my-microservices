package com.example.auth.api.v1.dto;

/**
 * The standard error body for /api/v1/** — RFC 7807 "Problem Details for HTTP APIs".
 *
 * <p>Every non-2xx response from a REST endpoint is serialized in this shape by
 * {@link com.example.auth.api.v1.ApiExceptionAdvice}. Machine-readable, so
 * clients can switch on {@code status} + {@code type} instead of pattern-matching
 * free-form English.
 *
 * <p>Field meanings from the RFC:
 * <ul>
 *   <li>{@code type}     — URI identifying the error class. "about:blank" for generic.</li>
 *   <li>{@code title}    — short, human-readable summary (never changes per instance).</li>
 *   <li>{@code status}   — HTTP status code (duplicated in body for convenience).</li>
 *   <li>{@code detail}   — specific info about THIS occurrence (e.g. which id was missing).</li>
 *   <li>{@code instance} — URI of the failing request (useful for grep).</li>
 * </ul>
 */
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance
) {}
