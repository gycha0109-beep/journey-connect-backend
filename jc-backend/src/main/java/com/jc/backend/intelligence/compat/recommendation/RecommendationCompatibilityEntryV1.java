package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification;

public record RecommendationCompatibilityEntryV1(
        String sourceObject,
        CompatibilityClassification classification,
        String commonContractOrMeaning,
        String authorityNote) {
    public RecommendationCompatibilityEntryV1 {
        java.util.Objects.requireNonNull(sourceObject, "sourceObject");
        java.util.Objects.requireNonNull(classification, "classification");
        java.util.Objects.requireNonNull(commonContractOrMeaning, "commonContractOrMeaning");
        java.util.Objects.requireNonNull(authorityNote, "authorityNote");
    }
}
