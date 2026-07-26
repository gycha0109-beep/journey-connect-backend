package com.jc.backend.recommendation.rca2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.web.util.OnCommittedResponseWrapper;
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
        AtomicBoolean submitted = new AtomicBoolean(false);
        OnCommittedResponseWrapper wrapper = new OnCommittedResponseWrapper(response) {
            @Override protected void onResponseCommitted() { submitOnce(request, submitted); }
        };
        filterChain.doFilter(request, wrapper);
        if (wrapper.isCommitted()) submitOnce(request, submitted);
    }

    private void submitOnce(HttpServletRequest request, AtomicBoolean submitted) {
        if (!submitted.compareAndSet(false, true)) return;
        for (var shadowRequest : registrar.drain(request)) {
            orchestrator.submitAfterResponseCommitted(shadowRequest, true);
        }
    }
}
