package com.jc.data.contract.v1.adapter.recommendation;

import java.util.Objects;
import java.util.regex.Pattern;

public record RecommendationP0AdapterPolicyV1(
        String adapterId,
        String adapterVersion,
        String mappingPolicyVersion,
        String outputFingerprintVersion,
        String sourceSchemaVersion,
        String targetContractVersion,
        String targetSchemaVersion,
        String targetCanonicalizationVersion) {

    public static final String GENERAL_EXPOSURE_AUTHORITY = "recommendation_general_exposure_v1";
    public static final String P2_EXPERIMENT_EXPOSURE_AUTHORITY = "recommendation_p2_experiment_exposure_v1";
    private static final Pattern VERSIONED = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*");

    public RecommendationP0AdapterPolicyV1 {
        adapterId = requireVersion(adapterId, "adapterId");
        adapterVersion = requireVersion(adapterVersion, "adapterVersion");
        mappingPolicyVersion = requireVersion(mappingPolicyVersion, "mappingPolicyVersion");
        outputFingerprintVersion = requireVersion(outputFingerprintVersion, "outputFingerprintVersion");
        sourceSchemaVersion = requireVersion(sourceSchemaVersion, "sourceSchemaVersion");
        targetContractVersion = requireVersion(targetContractVersion, "targetContractVersion");
        targetSchemaVersion = requireVersion(targetSchemaVersion, "targetSchemaVersion");
        targetCanonicalizationVersion = requireVersion(targetCanonicalizationVersion, "targetCanonicalizationVersion");
    }

    public static RecommendationP0AdapterPolicyV1 defaultPolicy() {
        return new RecommendationP0AdapterPolicyV1(
                "p0-recommendation-event-adapter-v1",
                "recommendation-p0-event-adapter-v1",
                "recommendation-p0-mapping-policy-v1",
                "recommendation-p0-adapter-output-sha256-v1",
                "recommendation-behavior-event-v1",
                "platform-event-v1",
                "user-behavior-event-v1",
                "platform-event-canonical-json-v1");
    }

    private static String requireVersion(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!VERSIONED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase versioned ID");
        }
        return value;
    }
}
