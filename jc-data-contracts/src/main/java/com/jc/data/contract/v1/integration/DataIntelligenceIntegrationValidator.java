package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class DataIntelligenceIntegrationValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        CrossTrackContractMapping mapping = context.contractMapping();
        if (!mapping.targetContractPresent()) {
            return List.of(CheckFactory.skipped(startOrder, "intelligence.contract.present", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                    context.sourceSnapshot().sourceContract(), mapping.targetContract(), CrossTrackIntegrationFailure.INTELLIGENCE_CONTRACT_MISSING, true));
        }
        checks.add(CheckFactory.check(startOrder, "intelligence.envelope.present", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "intelligence-input-snapshot-v1", mapping.targetContract(), true,
                CrossTrackIntegrationFailure.INTELLIGENCE_CONTRACT_MISSING, true, false));
        if (!mapping.domainMappingApproved()) {
            checks.add(CheckFactory.skipped(startOrder + 1, "intelligence.domain_mapping", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                    context.sourceSnapshot().sourceContract(), mapping.targetContract(), CrossTrackIntegrationFailure.INTELLIGENCE_DOMAIN_MAPPING_MISSING, true));
        } else {
            checks.add(CheckFactory.check(startOrder + 1, "intelligence.domain_mapping", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                    context.sourceSnapshot().sourceContract(), mapping.targetContract(), "approved", "approved", true,
                    CrossTrackIntegrationFailure.INTELLIGENCE_DOMAIN_MAPPING_MISSING, true, false));
        }
        boolean qualitySeparated = Boolean.TRUE.equals(context.sourceSnapshot().fields().get("qualityConfidenceSeparated"));
        checks.add(CheckFactory.check(startOrder + 2, "intelligence.quality_confidence_separation", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                context.sourceSnapshot().snapshotRef(), mapping.targetContract(), "separate", Boolean.toString(qualitySeparated), qualitySeparated,
                CrossTrackIntegrationFailure.INTELLIGENCE_QUALITY_SEMANTIC_MISMATCH, true, false));
        checks.add(CheckFactory.check(startOrder + 3, "intelligence.runtime_activation", CrossTrackIntegrationScope.DATA_INTELLIGENCE_INPUT,
                "DP-7", "Intelligence runtime", "false", Boolean.toString(context.intelligenceRuntimeActivationAttempted()),
                !context.intelligenceRuntimeActivationAttempted(), CrossTrackIntegrationFailure.INTELLIGENCE_RUNTIME_ACTIVATION_DETECTED, true, false));
        return List.copyOf(checks);
    }
}
