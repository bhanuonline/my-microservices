package com.example.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralised backend URLs + credentials. Overridden per profile in application.yml.
 */
@ConfigurationProperties(prefix = "shop.backends")
public class BackendProperties {

    private String gatewayUrl       = "http://localhost:8080";
    private String productQueryUrl  = "http://localhost:8088";
    private String adminUser        = "admin";
    private String adminPassword    = "admin123";

    public String getGatewayUrl()      { return gatewayUrl; }
    public void setGatewayUrl(String v)      { this.gatewayUrl = v; }

    public String getProductQueryUrl() { return productQueryUrl; }
    public void setProductQueryUrl(String v) { this.productQueryUrl = v; }

    public String getAdminUser()       { return adminUser; }
    public void setAdminUser(String v)       { this.adminUser = v; }

    public String getAdminPassword()   { return adminPassword; }
    public void setAdminPassword(String v)   { this.adminPassword = v; }
}
