package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import java.util.Objects;

public record SearchShadowSamplingPolicyV1(int basisPoints, PolicyVersion policyVersion) {
    public SearchShadowSamplingPolicyV1 {
        if (basisPoints < 0 || basisPoints > 10_000) throw new IllegalArgumentException("basisPoints must be 0..10000");
        Objects.requireNonNull(policyVersion, "policyVersion");
    }
}
