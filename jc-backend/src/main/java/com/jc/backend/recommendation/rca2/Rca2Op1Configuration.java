package com.jc.backend.recommendation.rca2;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;

/** OP-1 immutable configuration boundary. Any attempted activation fails application startup. */
public record Rca2Op1Configuration(
        String environment,
        boolean shadowEnabled,
        int configuredTrafficPercent,
        int effectiveTrafficPercent,
        int maxConfigurablePercent,
        String endpoint,
        Set<String> endpointAllowedHosts,
        Set<String> endpointAllowedSuffixes,
        String nonproductionNamespace,
        String databaseRoute,
        String candidateSource,
        String candidateProtocol,
        String candidateApiVersion,
        String credentialProvider,
        String allowlistStorage,
        String cohortSaltVersion,
        String cohortSaltMaterialHash,
        boolean automaticRamp,
        boolean manualEnablementImplemented) {

    public static final String ARTIFACT_VERSION = "op1-rca2-stage1-environment-access-v1";
    public static final int TARGET_PERCENT_CEILING = 1;

    public Rca2Op1Configuration {
        environment = required(environment, "environment");
        endpoint = normalized(endpoint);
        endpointAllowedHosts = Set.copyOf(endpointAllowedHosts == null ? Set.of() : endpointAllowedHosts);
        endpointAllowedSuffixes = Set.copyOf(endpointAllowedSuffixes == null ? Set.of() : endpointAllowedSuffixes);
        nonproductionNamespace = normalized(nonproductionNamespace);
        databaseRoute = normalized(databaseRoute);
        candidateSource = normalized(candidateSource);
        candidateProtocol = normalized(candidateProtocol);
        candidateApiVersion = normalized(candidateApiVersion);
        credentialProvider = normalized(credentialProvider);
        allowlistStorage = normalized(allowlistStorage);
        cohortSaltVersion = normalized(cohortSaltVersion);
        cohortSaltMaterialHash = normalized(cohortSaltMaterialHash);
        validate();
    }

    public static Rca2Op1Configuration from(Environment environment) {
        return new Rca2Op1Configuration(
                environment.getProperty("app.recommendation.rca2.environment", "missing"),
                strictBoolean(environment, "app.recommendation.rca2.shadow.enabled", false),
                strictInteger(environment, "app.recommendation.rca2.traffic-percent", 0),
                strictInteger(environment, "app.recommendation.rca2.op1.effective-traffic-percent", 0),
                strictInteger(environment, "app.recommendation.rca2.op1.max-configurable-percent", 1),
                environment.getProperty("app.recommendation.rca2.shadow.endpoint", ""),
                csv(environment.getProperty("app.recommendation.rca2.shadow.allowed-hosts", "")),
                csv(environment.getProperty("app.recommendation.rca2.shadow.allowed-host-suffixes", "")),
                environment.getProperty("app.recommendation.rca2.shadow.namespace", ""),
                environment.getProperty("app.recommendation.rca2.shadow.database-route", ""),
                environment.getProperty("app.recommendation.rca2.candidate.source", "UNRESOLVED"),
                environment.getProperty("app.recommendation.rca2.candidate.protocol", "UNRESOLVED"),
                environment.getProperty("app.recommendation.rca2.candidate.api-version", "UNRESOLVED"),
                environment.getProperty("app.recommendation.rca2.credential.provider", "UNRESOLVED"),
                environment.getProperty("app.recommendation.rca2.allowlist.storage", "UNRESOLVED"),
                environment.getProperty("app.recommendation.rca2.cohort.salt-version", ""),
                environment.getProperty("app.recommendation.rca2.cohort.salt-material-hash", ""),
                strictBoolean(environment, "app.recommendation.rca2.op1.automatic-ramp", false),
                strictBoolean(environment, "app.recommendation.rca2.op1.manual-enablement-implemented", false));
    }

    public boolean endpointConfigured() { return !"UNRESOLVED".equals(endpoint); }
    public boolean candidateSourceResolved() {
        return !"UNRESOLVED".equals(candidateSource)
                && !"UNRESOLVED".equals(candidateProtocol)
                && !"UNRESOLVED".equals(candidateApiVersion);
    }

    private void validate() {
        if (!Rca2RuntimeContracts.ENVIRONMENT.equals(environment)) {
            throw new IllegalArgumentException("OP-1 environment must remain isolated non-production");
        }
        if (shadowEnabled) throw new IllegalArgumentException("OP-1 shadow.enabled must remain false");
        if (configuredTrafficPercent < 0 || configuredTrafficPercent > TARGET_PERCENT_CEILING) {
            throw new IllegalArgumentException("traffic percent must be within 0..1");
        }
        if (configuredTrafficPercent != 0 || effectiveTrafficPercent != 0) {
            throw new IllegalArgumentException("OP-1 effective traffic must remain 0");
        }
        if (maxConfigurablePercent != TARGET_PERCENT_CEILING) {
            throw new IllegalArgumentException("max configurable percent must be 1");
        }
        if (automaticRamp) throw new IllegalArgumentException("automatic ramp is forbidden");
        if (manualEnablementImplemented) throw new IllegalArgumentException("manual enablement belongs to OP-3");
        if (!"UNRESOLVED".equals(databaseRoute)) {
            throw new IllegalArgumentException("database route is forbidden");
        }
    }

    private static boolean strictBoolean(Environment environment, String key, boolean defaultValue) {
        String value = environment.getProperty(key);
        if (value == null) return defaultValue;
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key + " malformed");
        return Boolean.parseBoolean(value);
    }

    private static int strictInteger(Environment environment, String key, int defaultValue) {
        String value = environment.getProperty(key);
        if (value == null) return defaultValue;
        if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException(key + " malformed");
        return Integer.parseInt(value);
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "UNRESOLVED" : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
