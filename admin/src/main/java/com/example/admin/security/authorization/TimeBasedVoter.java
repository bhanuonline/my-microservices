package com.example.admin.security.authorization;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Collection;

@Component
public class TimeBasedVoter implements AccessDecisionVoter<Object> {

    @Override
    public int vote(Authentication authentication, Object object,
                    Collection<ConfigAttribute> attributes) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.of(22, 0))) {
            // Deny all after 10 PM
            return ACCESS_DENIED;
        }
        return ACCESS_ABSTAIN;
    }

    @Override public boolean supports(ConfigAttribute attribute) { return true; }
    @Override public boolean supports(Class<?> clazz) { return true; }
}