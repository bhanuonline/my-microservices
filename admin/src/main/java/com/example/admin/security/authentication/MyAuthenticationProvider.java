package com.example.admin.security.authentication;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class MyAuthenticationProvider implements AuthenticationProvider {

    private final PasswordEncoder encoder;
    private final MyUserDetailsService userDetailsService;

    public MyAuthenticationProvider(PasswordEncoder encoder,
                                    MyUserDetailsService userDetailsService) {
        this.encoder = encoder;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String username = authentication.getName();
        String rawPwd = authentication.getCredentials().toString();

        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (encoder.matches(rawPwd, user.getPassword())) {
            // success: return a fully authenticated object
            return new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
        } else {
            throw new BadCredentialsException("Password mismatch");
        }
    }

    @Override
    public boolean supports(Class<?> authType) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authType);
    }
}