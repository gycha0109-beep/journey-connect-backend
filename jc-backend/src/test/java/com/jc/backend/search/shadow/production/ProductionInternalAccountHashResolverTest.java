package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class ProductionInternalAccountHashResolverTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedNumericSubjectIsHashedWithoutRetention() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("42")
                .issuedAt(Instant.parse("2026-07-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-21T00:00:00Z"))
                .build();
        var authentication = new UsernamePasswordAuthenticationToken(jwt, "n/a", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(new SecurityContextProductionInternalAccountHashResolver().currentAccountHash())
                .contains(SecurityContextProductionInternalAccountHashResolver.sha256("user:42"));
    }

    @Test
    void anonymousAndMalformedSubjectsAreExcluded() {
        assertThat(new SecurityContextProductionInternalAccountHashResolver().currentAccountHash()).isEmpty();

        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("anonymous").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, "n/a", java.util.List.of()));
        assertThat(new SecurityContextProductionInternalAccountHashResolver().currentAccountHash()).isEmpty();
    }
}
