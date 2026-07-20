package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import java.util.Objects;

public record SearchRuntimeFallbackV1(
        SearchRuntimeFallbackCode fallbackCode,
        SearchRuntimeFailureCode primaryFailureCode,
        PolicyVersion fallbackPolicyVersion) {
    public SearchRuntimeFallbackV1 {
        Objects.requireNonNull(fallbackCode, "fallbackCode");
        Objects.requireNonNull(primaryFailureCode, "primaryFailureCode");
        Objects.requireNonNull(fallbackPolicyVersion, "fallbackPolicyVersion");
    }
}
