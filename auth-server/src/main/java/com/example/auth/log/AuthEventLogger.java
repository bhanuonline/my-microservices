package com.example.auth.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authorization.event.AuthorizationGrantedEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuthEventLogger.class);

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        log.info("✅ LOGIN SUCCESS: principal={}, details={}",
                event.getAuthentication().getName(),
                event.getAuthentication().getDetails());
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        log.warn("❌ LOGIN FAILURE: principal={}, cause={}",
                event.getAuthentication().getPrincipal(),
                event.getException().getMessage());
    }
}