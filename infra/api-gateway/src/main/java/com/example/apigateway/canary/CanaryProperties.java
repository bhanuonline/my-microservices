package com.example.apigateway.canary;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.canary")
public class CanaryProperties {

    private boolean enabled = false;
    private String overrideHeader = "X-Canary";
    private String overrideValue = "force-v2";
    /** Response header injected on canary-served requests so clients can see which version they hit. */
    private String responseHeader = "X-Canary-Route";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getOverrideHeader() { return overrideHeader; }
    public void setOverrideHeader(String overrideHeader) { this.overrideHeader = overrideHeader; }

    public String getOverrideValue() { return overrideValue; }
    public void setOverrideValue(String overrideValue) { this.overrideValue = overrideValue; }

    public String getResponseHeader() { return responseHeader; }
    public void setResponseHeader(String responseHeader) { this.responseHeader = responseHeader; }
}
