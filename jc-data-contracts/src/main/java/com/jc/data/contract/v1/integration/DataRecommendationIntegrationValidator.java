package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class DataRecommendationIntegrationValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        CrossTrackContractMapping mapping = context.contractMapping();
        if (!mapping.targetContractPresent()) {
            return List.of(CheckFactory.skipped(startOrder, "recommendation.contract.present", context.definition().integrationScope(),
                    context.sourceSnapshot().sourceContract(), mapping.targetContract(), CrossTrackIntegrationFailure.RECOMMENDATION_CONTRACT_MISSING, true));
        }
        checks.add(CheckFactory.check(startOrder, "recommendation.schema.supported", context.definition().integrationScope(),
                mapping.sourceSchemaVersion(), mapping.targetSchemaVersion(), "supported", Boolean.toString(mapping.schemaSupported()), mapping.schemaSupported(),
                CrossTrackIntegrationFailure.RECOMMENDATION_SCHEMA_UNSUPPORTED, true, false));
        checks.add(CheckFactory.check(startOrder + 1, "recommendation.required_fields", context.definition().integrationScope(),
                mapping.sourceContract(), mapping.targetContract(), "complete", mapping.missingRequiredFields().toString(), mapping.requiredFieldsPresent(),
                CrossTrackIntegrationFailure.RECOMMENDATION_REQUIRED_FIELD_MISSING, true, false));
        checks.add(CheckFactory.check(startOrder + 2, "recommendation.semantic_mapping", context.definition().integrationScope(),
                mapping.sourceContract(), mapping.targetContract(), "equivalent", Boolean.toString(mapping.semanticsCompatible()), mapping.semanticsCompatible(),
                CrossTrackIntegrationFailure.RECOMMENDATION_FIELD_SEMANTIC_MISMATCH, true, false));
        checks.add(CheckFactory.check(startOrder + 3, "recommendation.unit_mapping", context.definition().integrationScope(),
                mapping.sourceContract(), mapping.targetContract(), "equivalent", Boolean.toString(mapping.unitsCompatible()), mapping.unitsCompatible(),
                CrossTrackIntegrationFailure.RECOMMENDATION_UNIT_MISMATCH, true, false));
        Object window = context.sourceSnapshot().fields().get(context.definition().integrationScope()
                == CrossTrackIntegrationScope.DATA_RECOMMENDATION_PROFILE ? "activityWindowDays" : "outcomeWindowSeconds");
        boolean windowValid = context.definition().integrationScope() == CrossTrackIntegrationScope.DATA_RECOMMENDATION_PROFILE
                ? window instanceof Integer days && List.of(7, 30, 90).contains(days)
                : window instanceof Long seconds && seconds.longValue() == 604800L;
        checks.add(CheckFactory.check(startOrder + 4, "recommendation.window", context.definition().integrationScope(),
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), context.definition().integrationScope()
                        == CrossTrackIntegrationScope.DATA_RECOMMENDATION_PROFILE ? "7|30|90 days" : "604800 seconds",
                String.valueOf(window), windowValid, CrossTrackIntegrationFailure.RECOMMENDATION_WINDOW_MISMATCH, true, false));
        boolean metric = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("metricSemanticsPreserved"));
        checks.add(CheckFactory.check(startOrder + 5, "recommendation.metric_semantics", context.definition().integrationScope(),
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "preserved", Boolean.toString(metric), metric,
                CrossTrackIntegrationFailure.RECOMMENDATION_METRIC_SEMANTIC_VIOLATION, true, false));
        if (context.definition().integrationScope() == CrossTrackIntegrationScope.DATA_RECOMMENDATION_EXPERIMENT_OUTCOME) {
            boolean exposure = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("authoritativeP2Exposure"));
            checks.add(CheckFactory.check(startOrder + 6, "recommendation.p2_exposure_authority", context.definition().integrationScope(),
                    context.sourceSnapshot().snapshotRef(), "recommendation_p2_experiment_exposure", "authoritative", Boolean.toString(exposure), exposure,
                    CrossTrackIntegrationFailure.RECOMMENDATION_AUTHORITY_VIOLATION, true, false));
        }
        checks.add(CheckFactory.check(startOrder + 7, "recommendation.production_write", context.definition().integrationScope(),
                "DP-7", "Recommendation", "false", Boolean.toString(context.recommendationProductionWriteAttempted()),
                !context.recommendationProductionWriteAttempted(), CrossTrackIntegrationFailure.RECOMMENDATION_PRODUCTION_WRITE_DETECTED, true, false));
        checks.add(CheckFactory.check(startOrder + 8, "recommendation.authority_preserved", context.definition().integrationScope(),
                mapping.sourceContract(), mapping.targetContract(), "current P1/P2 authority retained", "validation adapter required", true,
                CrossTrackIntegrationFailure.RECOMMENDATION_AUTHORITY_VIOLATION, false, true));
        return List.copyOf(checks);
    }
}
