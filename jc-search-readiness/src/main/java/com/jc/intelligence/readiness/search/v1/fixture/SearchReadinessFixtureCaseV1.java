package com.jc.intelligence.readiness.search.v1.fixture;

public record SearchReadinessFixtureCaseV1(
        String scenario,
        boolean proposalReady,
        int unresolvedActivationCount,
        String expectedProposalDecision,
        String expectedActivationDecision,
        boolean disabledEquivalenceExpected) {
    public SearchReadinessFixtureCaseV1 {
        if (scenario == null || !scenario.matches("[a-z][a-z0-9_]{0,79}")) throw new IllegalArgumentException("invalid scenario");
        if (unresolvedActivationCount < 0) throw new IllegalArgumentException("unresolvedActivationCount cannot be negative");
        if (expectedProposalDecision == null || !expectedProposalDecision.matches("[a-z][a-z0-9_]{0,79}")) throw new IllegalArgumentException("invalid expectedProposalDecision");
        if (expectedActivationDecision == null || !expectedActivationDecision.matches("[a-z][a-z0-9_]{0,79}")) throw new IllegalArgumentException("invalid expectedActivationDecision");
    }
}
