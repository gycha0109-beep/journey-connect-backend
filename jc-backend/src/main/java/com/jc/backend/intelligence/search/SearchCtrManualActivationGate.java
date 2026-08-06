package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SearchCtrManualActivationGate {

    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("local", "dev", "test", "stage");
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final String OPERATIONAL_REF_PATTERN =
            "[a-z][a-z0-9_-]{1,31}:[a-z0-9][a-z0-9._:/-]{2,126}";

    private final SearchCtrActivationPolicy.RuntimeMode authorizedMode;

    public SearchCtrManualActivationGate(SearchCtrActivationPolicy.RuntimeMode authorizedMode) {
        this.authorizedMode = Objects.requireNonNull(authorizedMode, "authorizedMode");
    }

    public static SearchCtrManualActivationGate current() {
        return new SearchCtrManualActivationGate(SearchCtrActivationPolicy.AUTHORIZED_RUNTIME_MODE);
    }

    public ApprovedRun approve(
            SearchCtrManualActivationProperties properties,
            String[] activeProfiles,
            Instant observedAt,
            boolean reliabilityCapabilityRequired) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(observedAt, "observedAt");

        require(properties.isEnabled(), "manual Search CTR activation is disabled");
        require(!properties.isKillSwitch(), "manual Search CTR activation kill switch is active");
        require(
                authorizedMode == SearchCtrActivationPolicy.RuntimeMode.NONPRODUCTION_MANUAL,
                "manual Search CTR runtime mode is not authorized");
        require(!SearchCtrActivationPolicy.isFinalityWriteAuthorized(),
                "manual Search CTR runner must not authorize finality writes");
        require(reliabilityCapabilityRequired,
                "manual Search CTR activation requires explicit reliability startup verification");

        String environment = normalized(properties.getEnvironment());
        require(ALLOWED_ENVIRONMENTS.contains(environment),
                "manual Search CTR environment is not allowlisted");
        require(environment.equals(SearchCtrActivationPolicy.AUTHORIZED_MANUAL_ENVIRONMENT),
                "manual Search CTR environment is outside the SR-6F-G authorization");

        Set<String> profiles = Arrays.stream(activeProfiles == null ? new String[0] : activeProfiles)
                .filter(Objects::nonNull)
                .map(SearchCtrManualActivationGate::normalized)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        require(profiles.contains(environment),
                "manual Search CTR environment must match an active Spring profile");
        require(profiles.stream().noneMatch(PRODUCTION_PROFILES::contains),
                "manual Search CTR activation is forbidden with a production profile");

        String approvalRef = normalized(properties.getApprovalRef());
        require(approvalRef.matches(OPERATIONAL_REF_PATTERN),
                "manual Search CTR approval reference is invalid");
        require(approvalRef.equals(SearchCtrActivationPolicy.AUTHORIZED_MANUAL_APPROVAL_REF),
                "manual Search CTR approval reference is outside the SR-6F-G authorization");

        String producerBuildId = properties.getProducerBuildId() == null
                ? ""
                : properties.getProducerBuildId().trim();
        require(producerBuildId.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"),
                "manual Search CTR producer build is invalid");
        require(producerBuildId.startsWith(
                        SearchCtrActivationPolicy.AUTHORIZED_MANUAL_PRODUCER_BUILD_PREFIX),
                "manual Search CTR producer build is outside the SR-6F-G authorization");

        Instant windowStart = parseInstant(properties.getWindowStart());
        require(windowStart.equals(SearchCtrActivationPolicy.AUTHORIZED_MANUAL_WINDOW_START),
                "manual Search CTR window is outside the SR-6F-G authorization");
        SearchCtrActivationPolicy.Window window = new SearchCtrActivationPolicy.Window(
                windowStart,
                windowStart.plus(SearchCtrActivationPolicy.PROJECTION_WINDOW));
        require(SearchCtrActivationPolicy.isProvisionalEligible(window, observedAt),
                "manual Search CTR window is not provisionally eligible");

        String idempotencyKey = "search-ctr:"
                + window.start() + ":"
                + window.end() + ":"
                + SearchCtrActivationPolicy.POLICY_VERSION + ":"
                + producerBuildId;
        require(idempotencyKey.length() <= 160
                        && idempotencyKey.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$"),
                "manual Search CTR idempotency key exceeds the approved contract");

        return new ApprovedRun(
                environment,
                window,
                observedAt,
                idempotencyKey,
                producerBuildId,
                approvalRef);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("manual Search CTR window start is required");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(
                    "manual Search CTR window start must be an ISO-8601 UTC instant",
                    exception);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public record ApprovedRun(
            String environment,
            SearchCtrActivationPolicy.Window window,
            Instant observedAt,
            String idempotencyKey,
            String producerBuildId,
            String approvalRef) {}
}
