package com.example.auth.controller.admin;

import com.example.auth.config.FeatureFlags;
import com.example.auth.dto.ClientForm;
import com.example.auth.service.admin.ClientAdminService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Browser admin pages for OAuth clients. Delegates all business logic to
 * {@link ClientAdminService}; this class just handles HTTP + view rendering.
 *
 * <p>URL map:
 * <pre>
 *   GET  /admin/clients             list
 *   GET  /admin/clients/new         empty form
 *   POST /admin/clients             create → redirect to list
 *   GET  /admin/clients/{id}/edit   pre-filled form
 *   POST /admin/clients/{id}        update → redirect to list
 *   POST /admin/clients/{id}/delete delete → redirect to list
 * </pre>
 *
 * <p>Form validation:
 * <ul>
 *   <li>{@code @Valid} triggers Bean Validation (see {@link ClientForm} annotations).</li>
 *   <li>PKCE feature-2 rule is enforced by
 *       {@link ClientAdminService#validatePkce(ClientForm)}; error is attached
 *       via {@code BindingResult.reject()} and shown in the template's global
 *       error banner.</li>
 * </ul>
 *
 * <p>Feature flags: injected once, added to the model as {@code features} so
 * templates can conditionally render UI (checkboxes, columns) based on what's on.
 */
@Controller
@Profile("jdbc")
@RequestMapping("/admin/clients")
public class AdminClientController {

    private static final List<String> AUTH_METHODS =
            List.of("client_secret_basic", "client_secret_post", "none");
    private static final List<String> GRANT_TYPES =
            List.of("authorization_code", "refresh_token", "client_credentials");

    private final ClientAdminService service;
    private final FeatureFlags flags;

    public AdminClientController(ClientAdminService service, FeatureFlags flags) {
        this.service = service;
        this.flags = flags;
    }

    @GetMapping
    public String list(Model model) {
        List<Map<String, Object>> clients = service.listAll();
        model.addAttribute("clients", clients);
        return "admin/clients/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        ClientForm form = new ClientForm();
        // Pre-check the rotation box when the feature is on and default-for-new is set.
        if (flags.getRefreshTokenRotation().isEnabled()
                && flags.getRefreshTokenRotation().isDefaultForNew()) {
            form.setRotateRefreshTokens(true);
        }
        // Safe default under PKCE enforcement — admin still has to add 'none' authMethod
        // to make it public; pre-checking here just means "if you go public, PKCE is on".
        if (flags.getPkce().isEnforceForPublicClients()) {
            form.setRequireProofKey(true);
        }
        model.addAttribute("form", form);
        model.addAttribute("mode", "new");
        addFormOptions(model);
        return "admin/clients/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ClientForm form,
                          BindingResult binding,
                          Model model) {
        service.validatePkce(form).ifPresent(msg ->
            binding.reject("requireProofKey", msg));
        if (binding.hasErrors()) {
            model.addAttribute("mode", "new");
            addFormOptions(model);
            return "admin/clients/form";
        }
        service.save(form);
        return "redirect:/admin/clients";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        RegisteredClient rc = service.findById(id);
        if (rc == null) return "redirect:/admin/clients";
        ClientForm form = service.toForm(rc);
        model.addAttribute("form", form);
        model.addAttribute("mode", "edit");
        addFormOptions(model);
        return "admin/clients/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable String id,
                          @Valid @ModelAttribute("form") ClientForm form,
                          BindingResult binding,
                          Model model) {
        form.setId(id);
        service.validatePkce(form).ifPresent(msg ->
            binding.reject("requireProofKey", msg));
        if (binding.hasErrors()) {
            model.addAttribute("mode", "edit");
            addFormOptions(model);
            return "admin/clients/form";
        }
        service.save(form);
        return "redirect:/admin/clients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        service.deleteById(id);
        return "redirect:/admin/clients";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("allAuthMethods", AUTH_METHODS);
        model.addAttribute("allGrantTypes", GRANT_TYPES);
        model.addAttribute("features", flags);
    }
}
