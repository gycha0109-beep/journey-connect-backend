package com.jc.backend.recommendation.rca2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public final class Rca2PostResponseFilter extends OncePerRequestFilter {
    private final Rca2RequestRegistrar registrar;
    private final Rca2RuntimeOrchestrator orchestrator;

    public Rca2PostResponseFilter(Rca2RequestRegistrar registrar, Rca2RuntimeOrchestrator orchestrator) {
        this.registrar = registrar;
        this.orchestrator = orchestrator;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        boolean committed = response.isCommitted();
        for (var shadowRequest : registrar.drain(request)) {
            orchestrator.submitAfterResponseCommitted(shadowRequest, committed);
        }
    }
}
