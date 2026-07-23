package com.jc.data.contract.v1.integration;

import java.util.Set;

public record CrossTrackIntegrationPolicy(
        String integrationPolicyVersion,
        Set<String> supportedSourceContracts,
        Set<String> supportedTargetContracts,
        Set<String> supportedSchemaVersions,
        Set<String> requiredQualityPolicyVersions,
        CrossTrackQualityVerdictStatus requiredQualityVerdict) {
    public static final String VERSION = "data-cross-track-integration-policy-v1";
    public CrossTrackIntegrationPolicy {
        integrationPolicyVersion = IntegrationSupport.version(integrationPolicyVersion, "integrationPolicyVersion");
        supportedSourceContracts = Set.copyOf(supportedSourceContracts);
        supportedTargetContracts = Set.copyOf(supportedTargetContracts);
        supportedSchemaVersions = Set.copyOf(supportedSchemaVersions);
        requiredQualityPolicyVersions = Set.copyOf(requiredQualityPolicyVersions);
        if (requiredQualityVerdict == null) throw new NullPointerException("requiredQualityVerdict");
    }
    public static CrossTrackIntegrationPolicy v1() {
        return new CrossTrackIntegrationPolicy(VERSION,
                Set.of("recommendation-profile-input-v1", "experiment-outcome-input-v1"),
                Set.of("recommendation-profile-input-v1", "recommendation-evaluation-dataset-v1",
                        "intelligence-input-snapshot-v1", "search-document-projection-v1"),
                Set.of("recommendation-profile-input-v1", "experiment-outcome-input-v1",
                        "recommendation-evaluation-dataset-v1", "intelligence-input-snapshot-v1",
                        "search-document-projection-v1"),
                Set.of("data-quality-policy-v1"), CrossTrackQualityVerdictStatus.VALIDATED);
    }
}
