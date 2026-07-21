package com.jc.backend.search.shadow.production;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ProductionSearchShadowPropertiesValidator {
    private static final String HASH_PATTERN = "[0-9a-f]{64}";
    private static final String OPERATIONAL_REF_PATTERN = "[a-z][a-z0-9_-]{1,31}:[a-z0-9][a-z0-9._:/-]{2,126}";

    private ProductionSearchShadowPropertiesValidator() { }

    public static ProductionSearchShadowRuntimeConfig validate(ProductionSearchShadowProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("production Search shadow properties are required");
        }
        validateApprovedBounds(properties);

        Set<String> normalizedAllowlist = normalizeAllowlist(properties.getAllowlistHashes());
        String approvalRef = optionalRef(properties.getActivationApprovalRef(), "activation approval");
        String approverRef = optionalRef(properties.getActivationApproverRef(), "activation approver");
        String executorRef = optionalRef(properties.getActivationExecutorRef(), "activation executor");
        String rollbackRef = optionalRef(properties.getRollbackOwnerRef(), "rollback owner");
        String metricRef = optionalRef(properties.getMetricVerificationRef(), "metric verification");
        Instant windowStart = optionalInstant(properties.getActivationWindowStart(), "activation window start");
        Instant windowEnd = optionalInstant(properties.getActivationWindowEnd(), "activation window end");
        if ((windowStart == null) != (windowEnd == null)) {
            throw new IllegalStateException("activation window start and end must be supplied together");
        }
        if (windowStart != null && !windowStart.isBefore(windowEnd)) {
            throw new IllegalStateException("activation window start must precede end");
        }

        boolean activationRequested = properties.isEnabled()
                && !properties.isKillSwitch()
                && properties.getSamplingBps() > 0;
        if (activationRequested) {
            require(!normalizedAllowlist.isEmpty(), "activation requires a non-empty approved account hash allowlist");
            require(approvalRef != null, "activation approval reference is required");
            require(approverRef != null, "activation approver reference is required");
            require(executorRef != null, "activation executor reference is required");
            require(rollbackRef != null, "rollback owner reference is required");
            require(metricRef != null, "metric verification reference is required");
            require(windowStart != null, "activation window is required");
        }

        boolean operationalInputsComplete = approvalRef != null
                && approverRef != null
                && executorRef != null
                && rollbackRef != null
                && metricRef != null
                && windowStart != null
                && windowEnd != null;
        int effective = activationRequested && operationalInputsComplete && !normalizedAllowlist.isEmpty()
                ? properties.getSamplingBps() : 0;

        return new ProductionSearchShadowRuntimeConfig(
                properties.isEnabled(),
                properties.isKillSwitch(),
                properties.getSamplingBps(),
                effective,
                normalizedAllowlist,
                approvalRef,
                approverRef,
                executorRef,
                rollbackRef,
                metricRef,
                windowStart,
                windowEnd,
                properties.getMaxCandidates(),
                properties.getCoreConcurrency(),
                properties.getMaxConcurrency(),
                properties.getQueueCapacity(),
                Duration.ofMillis(properties.getTimeoutMs()),
                Duration.ofMillis(properties.getHardTimeoutMs()));
    }

    private static void validateApprovedBounds(ProductionSearchShadowProperties properties) {
        if (properties.getMaxApprovedSamplingBps()
                != ProductionSearchShadowProperties.APPROVED_MAXIMUM_SAMPLING_BPS) {
            throw new IllegalStateException("approved production sampling ceiling must remain 10 BPS");
        }
        if (properties.getSamplingBps() < 0
                || properties.getSamplingBps() > ProductionSearchShadowProperties.APPROVED_MAXIMUM_SAMPLING_BPS) {
            throw new IllegalStateException("production sampling must be within 0..10 BPS");
        }
        if (properties.getCoreConcurrency() != 1 || properties.getMaxConcurrency() != 2) {
            throw new IllegalStateException("production concurrency must remain core=1 and max=2");
        }
        if (properties.getQueueCapacity() != 8) {
            throw new IllegalStateException("production queue capacity must remain 8");
        }
        if (properties.getTimeoutMs() != 200 || properties.getHardTimeoutMs() != 300) {
            throw new IllegalStateException("production timeout bounds must remain 200/300 ms");
        }
        if (properties.getHardTimeoutMs() < properties.getTimeoutMs()) {
            throw new IllegalStateException("hard timeout must not precede runtime timeout");
        }
        if (properties.getMaxCandidates() != 100) {
            throw new IllegalStateException("production candidate ceiling must remain 100");
        }
    }

    private static String optionalRef(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches(OPERATIONAL_REF_PATTERN)) {
            throw new IllegalStateException(field + " must be a bounded opaque reference");
        }
        return normalized;
    }

    private static Instant optionalInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(field + " must be an ISO-8601 UTC instant", exception);
        }
    }

    private static Set<String> normalizeAllowlist(Iterable<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return Set.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String item = value.trim().toLowerCase(Locale.ROOT);
            if (!item.matches(HASH_PATTERN)) {
                throw new IllegalStateException("allowlist entries must be lowercase SHA-256 values");
            }
            if (!normalized.add(item)) {
                throw new IllegalStateException("duplicate allowlist hash is prohibited");
            }
            if (normalized.size() > 3) {
                throw new IllegalStateException("internal production cohort cannot exceed three accounts");
            }
        }
        return Set.copyOf(normalized);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
