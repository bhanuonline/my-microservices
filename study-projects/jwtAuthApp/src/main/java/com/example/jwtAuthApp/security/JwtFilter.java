package com.example.jwtAuthApp.security;

import com.example.jwtAuthApp.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private AuthService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = header.substring(7);
        Claims claims = jwtUtil.extractClaims(token);
        String username = (String) claims.get("username");

        var userDetails = userService.loadUserByUsername(username);

        //var auth = new UsernamePasswordAuthenticationToken(
                //userDetails, null, userDetails.getAuthorities());

        //SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }
}
