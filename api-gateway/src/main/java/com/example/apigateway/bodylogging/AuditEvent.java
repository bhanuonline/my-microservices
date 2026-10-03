package com.example.apigateway.bodylogging;

/**
 * Data container for a single audit log entry. Mutated across the pre/post phases
 * of BodyLoggingGlobalFilter, then emitted once at the end.
 */
public class AuditEvent {

    private String correlationId;
    private String routeId;
    private String method;
    private String path;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private String principalName;

    private int status;
    private long durationMs;

    /** May be null if excluded, binary, or too large. */
    private String requestBody;
    private String responseBody;

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}
