package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Server-side password strength rules applied at EVERY password entry point
 * (signup, admin reset/create, self-service change).
 *
 * Every rule is independently toggleable so the operator can relax any one.
 * Defaults are deliberately conservative — current admin/alex passwords
 * still pass — so turning this on doesn't break the running system.
 *
 * NOTE: existing bcrypt hashes are never re-validated. The policy only
 * applies when a NEW password is submitted. Harden later by adding a
 * "weak-password-at-login" check if needed.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "password.policy")
public class PasswordPolicyProperties {

    /** Master switch. false → every rule skipped. */
    private boolean enabled = true;

    // ---------- length ----------
    private int minLength = 6;
    private int maxLength = 72;    // bcrypt only hashes the first 72 bytes anyway

    // ---------- character classes ----------
    /** Require at least one letter AND at least one digit. */
    private boolean requireLetterAndDigit = true;

    // ---------- context ----------
    /** Reject if password equals (case-insensitive) username or email. */
    private boolean rejectEqualsUsernameOrEmail = true;

    // ---------- common-password list ----------
    /** Load security/common-passwords.txt and reject matches. */
    private boolean rejectCommonPasswords = false;

    // ---------- repeating runs ----------
    /** Reject if any single character repeats N times in a row. 0 disables. */
    private int maxRepeatingChars = 0;
}
