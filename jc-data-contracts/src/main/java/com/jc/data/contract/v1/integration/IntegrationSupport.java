package com.jc.data.contract.v1.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class IntegrationSupport {
    private static final Pattern VERSION = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:~-]{0,179}$");
    private IntegrationSupport() { }
    static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is blank");
        return normalized;
    }
    static String version(String value, String field) {
        value = text(value, field);
        if (!VERSION.matcher(value).matches()) throw new IllegalArgumentException(field + " is not versioned");
        return value;
    }
    static String fingerprint(String value, String field) {
        value = text(value, field);
        if (!FINGERPRINT.matcher(value).matches()) throw new IllegalArgumentException(field + " is not SHA-256");
        return value;
    }
    static String reference(String value, String field) {
        value = text(value, field);
        if (!REF.matcher(value).matches()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }
    static Instant instant(Instant value, String field) { return Objects.requireNonNull(value, field); }
    static <T> List<T> list(List<T> value, String field) { return List.copyOf(Objects.requireNonNull(value, field)); }
    static <K,V> Map<K,V> map(Map<K,V> value, String field) { return Map.copyOf(Objects.requireNonNull(value, field)); }
    static boolean namespaceSupported(String value) {
        return value != null && (value.matches("^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$")
                || value.matches("^user:[1-9][0-9]*$"));
    }
}
