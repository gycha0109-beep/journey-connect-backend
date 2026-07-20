package com.jc.intelligence.wiring.search.v1;

@FunctionalInterface
public interface SearchShadowSampler {
    SearchShadowSamplingDecisionV1 decide(String stableCorrelationId, SearchShadowSamplingPolicyV1 policy);
}
