package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import java.math.BigInteger;
import java.util.Objects;

public final class DeterministicSearchShadowSampler implements SearchShadowSampler {
    @Override public SearchShadowSamplingDecisionV1 decide(String stableCorrelationId, SearchShadowSamplingPolicyV1 policy) {
        Objects.requireNonNull(policy, "policy");
        if (stableCorrelationId == null || stableCorrelationId.isBlank() || !stableCorrelationId.equals(stableCorrelationId.trim())) {
            throw new IllegalArgumentException("stableCorrelationId is required");
        }
        String fingerprint = SearchShadowFingerprintV1.sha256(
                "search-shadow-sampling-v1\n" + policy.policyVersion().value() + "\n" + stableCorrelationId);
        int bucket = new BigInteger(fingerprint.substring(0, 16), 16).mod(BigInteger.valueOf(10_000)).intValueExact();
        return new SearchShadowSamplingDecisionV1(bucket < policy.basisPoints(), bucket, policy.basisPoints(), fingerprint);
    }
}
