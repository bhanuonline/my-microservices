package com.angle.trading.controller;

import com.angle.trading.persistence.AppUserEntity;
import com.angle.trading.user.PasswordPolicyService;
import com.angle.trading.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin user management pages. Mounted under /admin/users so it inherits the
 * admin security chain (requires ROLE_ADMIN).
 *
 *   GET  /admin/users                     list all users
 *   GET  /admin/users/new                 create form
 *   POST /admin/users                     create (form submit)
 *   GET  /admin/users/{id}                edit form
 *   POST /admin/users/{id}                update (form submit)
 *   POST /admin/users/{id}/password       admin resets a user's password
 *   POST /admin/users/{id}/enable         toggle enabled
 *   POST /admin/users/{id}/delete         delete user (with self-protect guard)
 */
@Slf4j
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;
    private final PasswordPolicyService passwordPolicy;

    // ---------- list ----------

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String flash,
                       @RequestParam(required = false) String error) {
        model.addAttribute("users", userService.findAll());
        if (flash != null) model.addAttribute("flash", flash);
        if (error != null) model.addAttribute("error", error);
        return "admin/users/list";
    }

    // ---------- create ----------

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new AppUserEntity());
        model.addAttribute("mode", "create");
        return "admin/users/form";
    }

    @PostMapping
    public String create(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam(required = false) String email,
                         @RequestParam(defaultValue = "ROLE_USER") String role,
                         @RequestParam(defaultValue = "false") boolean enabled,
                         RedirectAttributes ra) {
        try {
            passwordPolicy.validateOrThrow(username, email, password);
            userService.create(username.trim(), password, email, role, enabled);
            ra.addAttribute("flash", "User " + username + " created");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
            return "redirect:/admin/users";
        }
    }

    // ---------- edit ----------

    @GetMapping("/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        AppUserEntity u = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        model.addAttribute("user", u);
        model.addAttribute("mode", "edit");
        return "admin/users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String role,
                         @RequestParam(required = false) Boolean enabled,
                         RedirectAttributes ra) {
        try {
            userService.update(id, email, role, enabled);
            ra.addAttribute("flash", "User updated");
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------- password reset (admin only) ----------

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String newPassword,
                                RedirectAttributes ra) {
        try {
            AppUserEntity u = userService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
            passwordPolicy.validateOrThrow(u.getUsername(), u.getEmail(), newPassword);
            userService.resetPassword(id, newPassword);
            ra.addAttribute("flash", "Password reset for user id " + id);
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------- enable / disable ----------

    @PostMapping("/{id}/enable")
    public String toggleEnabled(@PathVariable Long id,
                                 @RequestParam boolean on,
                                 RedirectAttributes ra,
                                 Authentication auth) {
        AppUserEntity u = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        // Guard: don't let an admin disable themselves (would lock them out)
        if (!on && auth != null && u.getUsername().equals(auth.getName())) {
            ra.addAttribute("error", "You can't disable your own account");
            return "redirect:/admin/users";
        }
        userService.update(id, null, null, on);
        ra.addAttribute("flash", "User " + u.getUsername() + " " + (on ? "enabled" : "disabled"));
        return "redirect:/admin/users";
    }

    // ---------- unlock ----------

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AppUserEntity u = userService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
            userService.unlock(id);
            ra.addAttribute("flash", "User " + u.getUsername() + " unlocked");
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------- 2FA reset (admin override — "lost my phone") ----------

    @PostMapping("/{id}/2fa/reset")
    public String resetTotp(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AppUserEntity u = userService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
            userService.adminDisableTotp(id);
            ra.addAttribute("flash", "2FA cleared for " + u.getUsername() + ". They can re-enrol from /account.");
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ---------- delete ----------

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         RedirectAttributes ra,
                         Authentication auth) {
        AppUserEntity u = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        // Guard: don't let an admin delete themselves
        if (auth != null && u.getUsername().equals(auth.getName())) {
            ra.addAttribute("error", "You can't delete your own account");
            return "redirect:/admin/users";
        }
        userService.delete(id);
        ra.addAttribute("flash", "User " + u.getUsername() + " deleted");
        return "redirect:/admin/users";
    }
}
