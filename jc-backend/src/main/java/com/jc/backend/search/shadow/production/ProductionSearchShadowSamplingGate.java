package com.jc.backend.search.shadow.production;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.wiring.search.v1.DeterministicSearchShadowSampler;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingPolicyV1;

public final class ProductionSearchShadowSamplingGate {
    private static final PolicyVersion POLICY = new PolicyVersion("search-shadow-production-pilot-sampling-v1");
    private final int basisPoints;
    private final DeterministicSearchShadowSampler sampler = new DeterministicSearchShadowSampler();

    public ProductionSearchShadowSamplingGate(int basisPoints) {
        if (basisPoints < 0 || basisPoints > ProductionSearchShadowProperties.APPROVED_MAXIMUM_SAMPLING_BPS) {
            throw new IllegalArgumentException("production sampling must be 0..10 BPS");
        }
        this.basisPoints = basisPoints;
    }

    public int basisPoints() { return basisPoints; }

    public SearchShadowSamplingDecisionV1 decide(String stableOpaqueAccountHash) {
        if (stableOpaqueAccountHash == null || !stableOpaqueAccountHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("stable opaque account hash is required");
        }
        return sampler.decide(stableOpaqueAccountHash, new SearchShadowSamplingPolicyV1(basisPoints, POLICY));
    }
}
