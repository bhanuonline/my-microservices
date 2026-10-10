package com.example.auth.controller.oauth;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.*;

/**
 * Renders the OAuth "authorize this app?" screen after user login.
 *
 * <p>When it appears:
 * <ol>
 *   <li>Third-party app (say "MyCoolApp") redirects the user's browser to
 *       {@code /oauth2/authorize?client_id=my-cool-app&scope=openid+profile+email…}</li>
 *   <li>User logs in with their auth-server credentials.</li>
 *   <li>Spring checks the client's config — if
 *       {@code requireAuthorizationConsent=true}, it delegates to this page.</li>
 *   <li>User sees the scope checkboxes, un-checks anything they don't want to
 *       grant, clicks Approve.</li>
 *   <li>Form POSTs back to {@code /oauth2/authorize} with the selected scopes.</li>
 *   <li>Spring writes the grant to {@code oauth2_authorization_consent} and
 *       redirects the browser to the app's redirect_uri with {@code ?code=...}</li>
 * </ol>
 *
 * <p>How Spring finds this controller:
 * {@code SecurityConfig.authServerSecurityFilterChain} calls
 * {@code .authorizationEndpoint(e -> e.consentPage("/oauth2/consent"))} — that
 * tells Spring "hand off consent rendering to this URL instead of your default
 * page". So Spring itself passes the query params ({@code client_id}, {@code scope},
 * {@code state}) into this controller.
 *
 * <p>Feature 8 detail: previously-granted scopes are pre-checked using
 * {@link OAuth2AuthorizationConsentService#findById}. So if the user granted
 * "profile" last week, this week's "profile+email" request shows profile
 * checked, email unchecked — they only see the delta.
 */
@Controller
@Profile("jdbc")
public class ConsentController {

    private static final Map<String, String> SCOPE_DESCRIPTIONS = Map.of(
            "openid",  "Verify your identity",
            "profile", "Access your profile (name, avatar)",
            "email",   "See your email address",
            "read",    "Read your data",
            "write",   "Modify your data"
    );

    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationConsentService consents;

    public ConsentController(RegisteredClientRepository clients,
                             OAuth2AuthorizationConsentService consents) {
        this.clients = clients;
        this.consents = consents;
    }

    @GetMapping("/oauth2/consent")
    public String consent(Principal principal,
                          @RequestParam("client_id") String clientId,
                          @RequestParam("scope") String scope,
                          @RequestParam("state") String state,
                          Model model) {
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            model.addAttribute("error", "Unknown client: " + clientId);
            return "oauth2/consent";
        }

        // Scopes the user is being asked to grant right now
        Set<String> requested = new LinkedHashSet<>(Arrays.asList(scope.split("\\s+")));

        // Scopes the user previously granted for this client (pre-check them)
        Set<String> previouslyGranted = Optional.ofNullable(
                consents.findById(client.getId(), principal.getName()))
                .map(c -> c.getScopes())
                .orElse(Set.of());

        List<ScopeView> views = requested.stream()
                .map(s -> new ScopeView(
                        s,
                        SCOPE_DESCRIPTIONS.getOrDefault(s, s),
                        previouslyGranted.contains(s)))
                .toList();

        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("state", state);
        model.addAttribute("scopes", views);
        model.addAttribute("principalName", principal.getName());
        return "oauth2/consent";
    }

    /** Row on the template — 1 per requested scope. */
    public record ScopeView(String name, String description, boolean previouslyGranted) {}
}
