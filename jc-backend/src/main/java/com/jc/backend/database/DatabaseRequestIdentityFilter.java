package com.jc.backend.database;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Binds only the verified JWT subject; request parameters and headers are never trusted. */
@Component
public final class DatabaseRequestIdentityFilter extends OncePerRequestFilter {

    private final DatabaseRequestIdentity requestIdentity;

    public DatabaseRequestIdentityFilter(DatabaseRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(jwt.getToken().getSubject());
        } catch (NumberFormatException exception) {
            throw new ServletException("Authenticated JWT subject must be a positive numeric user ID", exception);
        }
        if (userId <= 0) {
            throw new ServletException("Authenticated JWT subject must be a positive numeric user ID");
        }

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(userId)) {
            filterChain.doFilter(request, response);
        }
    }
}
