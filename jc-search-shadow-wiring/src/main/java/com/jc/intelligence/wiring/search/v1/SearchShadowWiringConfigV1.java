package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Duration;
import java.util.Objects;

public record SearchShadowWiringConfigV1(
        SearchShadowWiringMode mode,
        String activeProfile,
        boolean explicitAllow,
        int sampleBasisPoints,
        Duration timeout,
        int queueCapacity,
        int maxConcurrency,
        PolicyVersion samplingPolicyVersion,
        ProducerBuildId producerBuildId) {
    public static final String TEST_PROFILE = "search-shadow-test";
    public static final String STAGE_PROFILE = "search-shadow-stage";
    public SearchShadowWiringConfigV1 {
        Objects.requireNonNull(mode, "mode");
        if (activeProfile != null) {
            activeProfile = activeProfile.trim();
            if (activeProfile.isEmpty() || activeProfile.length() > 80 || activeProfile.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("activeProfile must be safe trimmed text");
            }
        }
        if (sampleBasisPoints < 0 || sampleBasisPoints > 10_000) throw new IllegalArgumentException("sampleBasisPoints must be 0..10000");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("timeout must be greater than zero and at most 30 seconds");
        }
        if (queueCapacity < 1 || queueCapacity > 10_000) throw new IllegalArgumentException("queueCapacity must be 1..10000");
        if (maxConcurrency < 1 || maxConcurrency > 1_000) throw new IllegalArgumentException("maxConcurrency must be 1..1000");
        Objects.requireNonNull(samplingPolicyVersion, "samplingPolicyVersion");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
    }
    public static SearchShadowWiringConfigV1 disabledByDefault(ProducerBuildId build) {
        return new SearchShadowWiringConfigV1(SearchShadowWiringMode.DISABLED, null, false, 0,
                Duration.ofMillis(100), 1, 1, new PolicyVersion("search-shadow-sampling-policy-v1"), build);
    }
}
