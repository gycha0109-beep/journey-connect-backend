package com.jc.data.contract.v1.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

final class QualityContractSupport {
    private static final Pattern VERSION = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern REFERENCE = Pattern.compile("^[a-z][a-z0-9_]*:[A-Za-z0-9][A-Za-z0-9._:~-]{0,159}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]*$");

    private QualityContractSupport() { }

    static String version(String value, String field) {
        String result = token(value, field, 96);
        if (!VERSION.matcher(result).matches()) throw new IllegalArgumentException(field + " is not a version");
        return result;
    }

    static String fingerprint(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!FINGERPRINT.matcher(value).matches()) throw new IllegalArgumentException(field + " is not SHA-256 hex");
        return value;
    }

    static String reference(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!REFERENCE.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    static String token(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    static Instant retention(Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (expiresAt.isBefore(createdAt.plus(90, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("retention must be at least 90 days");
        }
        return expiresAt;
    }

    static BigDecimal decimal(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        return value.stripTrailingZeros();
    }
}
