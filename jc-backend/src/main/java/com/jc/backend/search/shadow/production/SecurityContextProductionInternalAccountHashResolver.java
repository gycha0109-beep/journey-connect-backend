package com.jc.backend.search.shadow.production;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves an ephemeral SHA-256 cohort key; the raw subject is never stored or logged. */
public final class SecurityContextProductionInternalAccountHashResolver
        implements ProductionInternalAccountHashResolver {
    @Override
    public Optional<String> currentAccountHash() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                return Optional.empty();
            }
            String subject = jwt.getSubject();
            if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) {
                return Optional.empty();
            }
            return Optional.of(sha256("user:" + subject));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
