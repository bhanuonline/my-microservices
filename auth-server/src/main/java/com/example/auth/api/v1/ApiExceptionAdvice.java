package com.example.auth.api.v1;

import com.example.auth.api.v1.dto.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns thrown exceptions from any /api/v1/** controller into RFC 7807 JSON.
 *
 * <p>RFC 7807 ("Problem Details for HTTP APIs") is a small standard for
 * machine-readable error bodies. Every response looks like:
 * <pre>
 *   Content-Type: application/problem+json
 *   {
 *     "type":     "about:blank",
 *     "title":    "Not Found",
 *     "status":   404,
 *     "detail":   "client id=abc123",
 *     "instance": "/api/v1/admin/clients/abc123"
 *   }
 * </pre>
 *
 * <p>Handler mapping:
 * <table>
 *   <tr><th>Exception</th><th>HTTP status</th></tr>
 *   <tr><td>NotFoundException (our own)</td><td>404</td></tr>
 *   <tr><td>MethodArgumentNotValidException (@Valid failed)</td><td>400</td></tr>
 *   <tr><td>IllegalArgumentException</td><td>400</td></tr>
 *   <tr><td>AccessDeniedException</td><td>403</td></tr>
 *   <tr><td>Anything else</td><td>500</td></tr>
 * </table>
 *
 * <p>Scoped by {@code basePackages} — browser controllers keep their normal
 * HTML error pages; only /api/v1/** returns problem+json.
 */
@RestControllerAdvice(basePackages = "com.example.auth.api.v1")
@Profile("jdbc")
public class ApiExceptionAdvice {

    private static final String CT = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @ExceptionHandler(ClientRestController.NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ClientRestController.NotFoundException ex,
                                                   HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage()).findFirst().orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, "Validation Failed", detail, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> illegal(IllegalArgumentException ex,
                                                  HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(AccessDeniedException ex,
                                                 HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient scope", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> generic(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", ex.getMessage(), req);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title,
                                                    String detail, HttpServletRequest req) {
        ProblemDetail body = new ProblemDetail(
                "about:blank", title, status.value(), detail, req.getRequestURI());
        return ResponseEntity.status(status).header("Content-Type", CT).body(body);
    }
}
