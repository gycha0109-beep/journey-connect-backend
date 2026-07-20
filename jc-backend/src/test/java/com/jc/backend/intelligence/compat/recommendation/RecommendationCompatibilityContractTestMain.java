package com.jc.backend.intelligence.compat.recommendation;

public final class RecommendationCompatibilityContractTestMain {
    private RecommendationCompatibilityContractTestMain() {
    }

    public static void main(String[] args) throws Exception {
        int checks = new RecommendationCompatibilityContractAssertions().runAll();
        System.out.println("IP-1 recommendation compatibility checks passed: " + checks);
    }
}
