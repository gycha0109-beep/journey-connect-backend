package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Duration;
import java.util.Objects;

public record SearchShadowPolicyV1(
        SearchShadowMode mode,
        PolicyVersion shadowPolicyVersion,
        PolicyVersion comparisonPolicyVersion,
        Duration timeout,
        int topK,
        ProducerBuildId producerBuildId) {
    public SearchShadowPolicyV1 {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(shadowPolicyVersion, "shadowPolicyVersion");
        Objects.requireNonNull(comparisonPolicyVersion, "comparisonPolicyVersion");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("timeout must be greater than zero and at most 30 seconds");
        }
        if (topK < 1 || topK > 100) throw new IllegalArgumentException("topK must be 1..100");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
    }

    public static SearchShadowPolicyV1 disabledByDefault(ProducerBuildId producerBuildId) {
        return new SearchShadowPolicyV1(
                SearchShadowMode.DISABLED,
                new PolicyVersion("search-shadow-policy-v1"),
                new PolicyVersion("search-shadow-comparison-policy-v1"),
                Duration.ofMillis(100),
                10,
                producerBuildId);
    }
}
