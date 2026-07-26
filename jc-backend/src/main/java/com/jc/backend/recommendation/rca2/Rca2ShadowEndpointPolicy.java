package com.jc.backend.recommendation.rca2;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Validates an isolated non-production read-only candidate endpoint without resolving DNS. */
public final class Rca2ShadowEndpointPolicy {
    public static final String OWNER = "OPERATIONS";
    public static final String ENVIRONMENT = "ISOLATED_NON_PRODUCTION_RUNTIME";
    public static final String READ_ONLY_METHOD = "POST";
    public static final String READ_ONLY_PATH = "/v1/candidates/read";
    public static final String REDIRECT_POLICY = "FORBIDDEN";

    public enum Rejection {
        NONE, MISSING, INVALID_URI, HTTPS_REQUIRED, USERINFO_FORBIDDEN, QUERY_FORBIDDEN,
        FRAGMENT_FORBIDDEN, LOCALHOST_FORBIDDEN, IP_LITERAL_FORBIDDEN, PRIVATE_ROUTE_FORBIDDEN,
        PRODUCTION_HOST_FORBIDDEN, HOST_NOT_ALLOWLISTED, PATH_NOT_ALLOWLISTED,
        PRODUCTION_NAMESPACE_FORBIDDEN, PRODUCTION_DATABASE_ROUTE_FORBIDDEN
    }

    public record Decision(boolean allowed, Rejection rejection, String hostClass) {
        public static Decision denied(Rejection rejection) {
            return new Decision(false, Objects.requireNonNull(rejection), "redacted");
        }
    }

    private static final Set<String> PRODUCTION_MARKERS = Set.of(
            "prod", "production", "live", "primary", "customer", "public");
    private final Set<String> allowedExactHosts;
    private final Set<String> allowedHostSuffixes;
    private final boolean testProfile;

    public Rca2ShadowEndpointPolicy(Set<String> allowedExactHosts, Set<String> allowedHostSuffixes, boolean testProfile) {
        this.allowedExactHosts = normalize(allowedExactHosts);
        this.allowedHostSuffixes = normalize(allowedHostSuffixes);
        this.testProfile = testProfile;
    }

    public Decision validate(String endpoint, String namespace, String databaseRoute) {
        if (endpoint == null || endpoint.isBlank()) return Decision.denied(Rejection.MISSING);
        final URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException exception) {
            return Decision.denied(Rejection.INVALID_URI);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return Decision.denied(Rejection.HTTPS_REQUIRED);
        if (uri.getRawUserInfo() != null) return Decision.denied(Rejection.USERINFO_FORBIDDEN);
        if (uri.getRawQuery() != null) return Decision.denied(Rejection.QUERY_FORBIDDEN);
        if (uri.getRawFragment() != null) return Decision.denied(Rejection.FRAGMENT_FORBIDDEN);
        String host = uri.getHost();
        if (host == null || host.isBlank()) return Decision.denied(Rejection.INVALID_URI);
        host = host.toLowerCase(Locale.ROOT);
        if ((host.equals("localhost") || host.endsWith(".localhost")) && !testProfile) {
            return Decision.denied(Rejection.LOCALHOST_FORBIDDEN);
        }
        if (isIpLiteral(host)) return Decision.denied(Rejection.IP_LITERAL_FORBIDDEN);
        if (host.endsWith(".local") || host.endsWith(".internal.local")) {
            return Decision.denied(Rejection.PRIVATE_ROUTE_FORBIDDEN);
        }
        if (containsProductionMarker(host)) return Decision.denied(Rejection.PRODUCTION_HOST_FORBIDDEN);
        if (!hostAllowed(host)) return Decision.denied(Rejection.HOST_NOT_ALLOWLISTED);
        if (!READ_ONLY_PATH.equals(uri.getPath())) return Decision.denied(Rejection.PATH_NOT_ALLOWLISTED);
        if (namespace == null || namespace.isBlank()) return Decision.denied(Rejection.PRODUCTION_NAMESPACE_FORBIDDEN);
        String normalizedNamespace = namespace.toLowerCase(Locale.ROOT);
        if (containsProductionMarker(normalizedNamespace)) return Decision.denied(Rejection.PRODUCTION_NAMESPACE_FORBIDDEN);
        if (databaseRoute != null && !databaseRoute.isBlank()) {
            return Decision.denied(Rejection.PRODUCTION_DATABASE_ROUTE_FORBIDDEN);
        }
        return new Decision(true, Rejection.NONE, "isolated_nonproduction_allowlisted_host");
    }

    private boolean hostAllowed(String host) {
        if (testProfile && host.equals("localhost")) return true;
        if (allowedExactHosts.contains(host)) return true;
        return allowedHostSuffixes.stream().anyMatch(suffix -> host.endsWith(suffix) && host.length() > suffix.length());
    }

    private static boolean containsProductionMarker(String value) {
        String[] tokens = value.split("[.\\-_]");
        for (String token : tokens) if (PRODUCTION_MARKERS.contains(token)) return true;
        return false;
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("[0-9.]+") || host.contains(":");
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null) return Set.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
