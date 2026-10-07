package com.example.backoffice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backoffice.backends")
public class BackendProperties {
    private String gatewayUrl        = "http://localhost:8080";
    private String productQueryUrl   = "http://localhost:8088";
    private String orderQueryUrl     = "http://localhost:8086";
    private String prometheusUrl     = "http://localhost:9090";
    private String grafanaUrl        = "http://localhost:3000";
    private String zipkinUrl         = "http://localhost:9411";
    private String notificationUrl   = "http://localhost:9999";
    private String adminUser         = "admin";
    private String adminPassword     = "admin123";

    public String getGatewayUrl()      { return gatewayUrl; }
    public void setGatewayUrl(String v){ this.gatewayUrl = v; }
    public String getProductQueryUrl() { return productQueryUrl; }
    public void setProductQueryUrl(String v){ this.productQueryUrl = v; }
    public String getOrderQueryUrl()   { return orderQueryUrl; }
    public void setOrderQueryUrl(String v){ this.orderQueryUrl = v; }
    public String getPrometheusUrl()   { return prometheusUrl; }
    public void setPrometheusUrl(String v){ this.prometheusUrl = v; }
    public String getGrafanaUrl()      { return grafanaUrl; }
    public void setGrafanaUrl(String v){ this.grafanaUrl = v; }
    public String getZipkinUrl()       { return zipkinUrl; }
    public void setZipkinUrl(String v){ this.zipkinUrl = v; }
    public String getNotificationUrl() { return notificationUrl; }
    public void setNotificationUrl(String v){ this.notificationUrl = v; }
    public String getAdminUser()       { return adminUser; }
    public void setAdminUser(String v){ this.adminUser = v; }
    public String getAdminPassword()   { return adminPassword; }
    public void setAdminPassword(String v){ this.adminPassword = v; }
}
