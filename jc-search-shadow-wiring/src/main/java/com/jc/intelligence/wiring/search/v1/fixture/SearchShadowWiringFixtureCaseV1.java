package com.jc.intelligence.wiring.search.v1.fixture;

public record SearchShadowWiringFixtureCaseV1(
        String scenario, String mode, String activeProfile, boolean explicitAllow,
        int sampleBasisPoints, String executorStatus, String circuitState, String expectedStatus) {
    public SearchShadowWiringFixtureCaseV1 {
        if (scenario == null || scenario.isBlank() || !scenario.equals(scenario.trim())) throw new IllegalArgumentException("scenario is required");
        if (mode == null || executorStatus == null || circuitState == null || expectedStatus == null) throw new IllegalArgumentException("fixture fields are required");
        if (sampleBasisPoints < 0 || sampleBasisPoints > 10_000) throw new IllegalArgumentException("sampleBasisPoints out of range");
    }
}
