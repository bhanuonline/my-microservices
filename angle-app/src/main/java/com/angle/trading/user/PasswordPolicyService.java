package com.angle.trading.user;

import com.angle.trading.config.PasswordPolicyProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates raw passwords against a configurable set of rules.
 *
 * Single entry point for every place in the app that writes a password:
 *   • SignupController       — new account via public signup
 *   • UserAdminController    — admin creates / resets a password
 *   • AccountController      — user changes their own password
 *
 * Each call returns a list of violations. Empty list = password OK.
 * Throwing variant {@link #validateOrThrow} converts that into an
 * IllegalArgumentException whose message is the UI-ready reason.
 *
 * The common-password list is loaded lazily on first use so startup cost
 * stays zero when the feature is disabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final String COMMON_PWD_RESOURCE = "security/common-passwords.txt";

    private final PasswordPolicyProperties props;

    private volatile Set<String> commonPasswords;   // lazy-loaded, immutable once set

    @PostConstruct
    void init() {
        log.info("PasswordPolicyService ready — enabled={} minLength={} requireLetterAndDigit={} rejectCommon={} rejectEqualsUser={} maxRepeat={}",
                props.isEnabled(), props.getMinLength(), props.isRequireLetterAndDigit(),
                props.isRejectCommonPasswords(), props.isRejectEqualsUsernameOrEmail(),
                props.getMaxRepeatingChars());
    }

    /**
     * Returns an empty list if the password passes every enabled rule.
     * Otherwise returns one short string per violation, suitable for UI.
     */
    public List<String> validate(String username, String email, String rawPassword) {
        if (!props.isEnabled()) return List.of();

        List<String> errors = new ArrayList<>();

        if (rawPassword == null || rawPassword.isEmpty()) {
            errors.add("Password is required");
            return errors;
        }

        int len = rawPassword.length();
        if (len < props.getMinLength()) {
            errors.add("Password must be at least " + props.getMinLength() + " characters");
        }
        if (len > props.getMaxLength()) {
            errors.add("Password must be at most " + props.getMaxLength() + " characters");
        }

        if (props.isRequireLetterAndDigit() && !hasLetterAndDigit(rawPassword)) {
            errors.add("Password must contain at least one letter and one digit");
        }

        if (props.isRejectEqualsUsernameOrEmail()) {
            String lower = rawPassword.toLowerCase();
            if (username != null && !username.isBlank() && lower.equals(username.toLowerCase())) {
                errors.add("Password must not equal your username");
            }
            if (email != null && !email.isBlank() && lower.equals(email.toLowerCase())) {
                errors.add("Password must not equal your email");
            }
            // Also catch "contains username" for short usernames (len >= 4),
            // which is slightly stronger than exact match.
            if (username != null && username.length() >= 4 && lower.contains(username.toLowerCase())) {
                errors.add("Password must not contain your username");
            }
        }

        if (props.getMaxRepeatingChars() > 0 && hasRepeatingRun(rawPassword, props.getMaxRepeatingChars())) {
            errors.add("Password must not repeat the same character " + props.getMaxRepeatingChars() + "+ times");
        }

        if (props.isRejectCommonPasswords() && isCommonPassword(rawPassword)) {
            errors.add("Password is too common — pick something less obvious");
        }

        return errors;
    }

    /**
     * Convenience: throw IllegalArgumentException with the first violation
     * (controllers already catch IAE and flash it to the UI). Full list is
     * included in the message so advanced users see every problem at once.
     */
    public void validateOrThrow(String username, String email, String rawPassword) {
        List<String> errors = validate(username, email, rawPassword);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(". ", errors));
        }
    }

    // ---------- helpers ----------

    private static boolean hasLetterAndDigit(String s) {
        boolean letter = false, digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) letter = true;
            else if (Character.isDigit(c)) digit = true;
            if (letter && digit) return true;
        }
        return false;
    }

    private static boolean hasRepeatingRun(String s, int threshold) {
        if (threshold < 2 || s.length() < threshold) return false;
        int run = 1;
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == s.charAt(i - 1)) {
                run++;
                if (run >= threshold) return true;
            } else {
                run = 1;
            }
        }
        return false;
    }

    private boolean isCommonPassword(String raw) {
        Set<String> set = commonPasswords;
        if (set == null) {
            set = loadCommonPasswords();
            commonPasswords = set;   // publish once; safe double-check
        }
        return set.contains(raw.toLowerCase());
    }

    private Set<String> loadCommonPasswords() {
        Set<String> set = new HashSet<>();
        ClassPathResource res = new ClassPathResource(COMMON_PWD_RESOURCE);
        if (!res.exists()) {
            log.warn("common-passwords.txt not found at classpath:{} — common-pwd check will accept everything", COMMON_PWD_RESOURCE);
            return Collections.emptySet();
        }
        try (InputStream in = res.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                set.add(line.toLowerCase());
            }
            log.info("Loaded {} common passwords from classpath:{}", set.size(), COMMON_PWD_RESOURCE);
        } catch (IOException e) {
            log.warn("Failed to read common-passwords.txt: {} — check will accept everything", e.getMessage());
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(set);
    }
}
