package com.example.auth.controller.admin;

import com.example.auth.dto.UserForm;
import com.example.auth.entity.AppUser;
import com.example.auth.service.admin.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Browser admin pages for user accounts. Delegates business logic to
 * {@link UserAdminService}. Same shape as {@link AdminClientController} but
 * over users instead of OAuth clients.
 *
 * <p>URL map:
 * <pre>
 *   GET  /admin/users                  list
 *   GET  /admin/users/new              empty form
 *   POST /admin/users                  create → redirect to list
 *   GET  /admin/users/{id}/edit        pre-filled form
 *   POST /admin/users/{id}             update → redirect to list
 *   POST /admin/users/{id}/delete      delete → redirect to list
 *   POST /admin/users/{id}/unlock      clear Feature 5 lockout → redirect
 * </pre>
 *
 * <p>The unlock endpoint is exposed here in addition to the REST version so
 * an admin can click a button in the browser after finding a locked user in
 * the list (badge is rendered by {@code admin/users/list.html} template).
 */
@Controller
@Profile("jdbc")
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final List<String> ROLES = List.of("ADMIN", "USER");

    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", service.listAll());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("mode", "new");
        model.addAttribute("allRoles", ROLES);
        return "admin/users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form,
                          BindingResult binding,
                          Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "new");
            model.addAttribute("allRoles", ROLES);
            return "admin/users/form";
        }
        service.save(form);
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser u = service.findById(id);
        if (u == null) return "redirect:/admin/users";
        model.addAttribute("form", service.toForm(u));
        model.addAttribute("mode", "edit");
        model.addAttribute("allRoles", ROLES);
        return "admin/users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("form") UserForm form,
                          BindingResult binding,
                          Model model) {
        form.setId(id);
        if (binding.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("allRoles", ROLES);
            return "admin/users/form";
        }
        service.save(form);
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.deleteById(id);
        return "redirect:/admin/users";
    }

    /** FEATURE 5: unlock a locked account. */
    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id) {
        service.unlock(id);
        return "redirect:/admin/users";
    }
}
