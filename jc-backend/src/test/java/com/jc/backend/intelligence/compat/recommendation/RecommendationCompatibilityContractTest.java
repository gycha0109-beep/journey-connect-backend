package com.jc.backend.intelligence.compat.recommendation;

import org.junit.jupiter.api.Test;

final class RecommendationCompatibilityContractTest {
    @Test
    void protectsCommonContractsAndRecommendationCompatibility() throws Exception {
        int checks = new RecommendationCompatibilityContractAssertions().runAll();
        org.junit.jupiter.api.Assertions.assertTrue(checks >= 100);
    }
}
