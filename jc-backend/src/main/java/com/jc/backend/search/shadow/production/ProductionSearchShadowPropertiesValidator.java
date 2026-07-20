package com.jc.backend.search.shadow.production;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ProductionSearchShadowPropertiesValidator {
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    private ProductionSearchShadowPropertiesValidator() { }

    public static ProductionSearchShadowRuntimeConfig validate(ProductionSearchShadowProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("production Search shadow properties are required");
        }
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
        Set<String> normalized = normalizeAllowlist(properties.getAllowlistHashes());
        int effective = properties.isEnabled() && !properties.isKillSwitch()
                ? properties.getSamplingBps() : 0;
        return new ProductionSearchShadowRuntimeConfig(
                properties.isEnabled(),
                properties.isKillSwitch(),
                properties.getSamplingBps(),
                effective,
                normalized,
                properties.getMaxCandidates(),
                properties.getCoreConcurrency(),
                properties.getMaxConcurrency(),
                properties.getQueueCapacity(),
                Duration.ofMillis(properties.getTimeoutMs()),
                Duration.ofMillis(properties.getHardTimeoutMs()));
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
}
