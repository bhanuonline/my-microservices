package com.example.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.admin")
public class AdminAuthProperties {

    /** Which admin UI to serve from /admin/ui. */
    public enum Ui { REACT, THYMELEAF, NONE }

    /**
     * UI mode. Controls what /admin/ui serves.
     *   REACT     — redirect to reactUrl (external React dev server / bundle)
     *   THYMELEAF — render server-side pages from the gateway itself
     *   NONE      — /admin/ui returns 404
     */
    private Ui ui = Ui.REACT;

    /** External URL for the React UI (used when ui=REACT). */
    private String reactUrl = "http://localhost:5173/";

    /** Any of these authorities on the caller's principal grants /admin/** access. */
    private List<String> requiredAuthorities = List.of("SCOPE_admin", "ROLE_ADMIN");

    /** JWT claim to read scopes from (space-separated string OR array). */
    private String scopeClaim = "scope";

    /** JWT claim to read roles from (array OR CSV string). */
    private String rolesClaim = "roles";

    /**
     * Escape hatch for demo/dev: JWT subs listed here are granted SCOPE_admin
     * regardless of actual claims. Useful when your auth-server doesn't emit
     * scopes/roles yet (e.g. client_credentials with just `admin` sub).
     * KEEP EMPTY IN PRODUCTION.
     */
    private List<String> demoAdminSubs = List.of("admin");

    public Ui getUi() { return ui; }
    public void setUi(Ui ui) { this.ui = ui; }

    public String getReactUrl() { return reactUrl; }
    public void setReactUrl(String reactUrl) { this.reactUrl = reactUrl; }

    public List<String> getRequiredAuthorities() { return requiredAuthorities; }
    public void setRequiredAuthorities(List<String> requiredAuthorities) { this.requiredAuthorities = requiredAuthorities; }

    public String getScopeClaim() { return scopeClaim; }
    public void setScopeClaim(String scopeClaim) { this.scopeClaim = scopeClaim; }

    public String getRolesClaim() { return rolesClaim; }
    public void setRolesClaim(String rolesClaim) { this.rolesClaim = rolesClaim; }

    public List<String> getDemoAdminSubs() { return demoAdminSubs; }
    public void setDemoAdminSubs(List<String> demoAdminSubs) { this.demoAdminSubs = demoAdminSubs; }
}
