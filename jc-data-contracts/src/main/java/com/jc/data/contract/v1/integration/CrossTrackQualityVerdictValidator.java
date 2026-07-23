package com.jc.data.contract.v1.integration;

import java.util.List;

public final class CrossTrackQualityVerdictValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int order) {
        CrossTrackQualityVerdictEvidence verdict = context.qualityVerdict();
        if (verdict == null) {
            return List.of(CheckFactory.skipped(order, "quality.verdict.present", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                    context.sourceSnapshot().snapshotRef(), "data-quality-policy-v1",
                    CrossTrackIntegrationFailure.QUALITY_VERDICT_MISSING, true));
        }
        if (!verdict.authoritative()) return List.of(CheckFactory.check(order, "quality.verdict.authority",
                CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY, verdict.verdictRef(), "Data quality authority", "true", "false", false,
                CrossTrackIntegrationFailure.QUALITY_VERDICT_AUTHORITY_VIOLATION, true, false));
        if (verdict.conflicted()) return List.of(CheckFactory.check(order, "quality.verdict.conflict",
                CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY, verdict.verdictRef(), context.sourceSnapshot().snapshotRef(), "false", "true", false,
                CrossTrackIntegrationFailure.QUALITY_VERDICT_CONFLICTED, true, false));
        if (!context.integrationPolicy().requiredQualityPolicyVersions().contains(verdict.qualityPolicyVersion())) {
            return List.of(CheckFactory.check(order, "quality.policy.supported", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                    verdict.qualityPolicyVersion(), context.integrationPolicy().integrationPolicyVersion(), "supported", "unsupported", false,
                    CrossTrackIntegrationFailure.QUALITY_POLICY_UNSUPPORTED, true, false));
        }
        if (!verdict.snapshotRef().equals(context.sourceSnapshot().snapshotRef())) {
            return List.of(CheckFactory.check(order, "quality.snapshot.binding", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                    verdict.snapshotRef(), context.sourceSnapshot().snapshotRef(), context.sourceSnapshot().snapshotRef(), verdict.snapshotRef(), false,
                    CrossTrackIntegrationFailure.QUALITY_SNAPSHOT_MISMATCH, true, false));
        }
        if (verdict.overallStatus() == CrossTrackQualityVerdictStatus.REJECTED) {
            return List.of(CheckFactory.check(order, "quality.verdict.status", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                    verdict.verdictRef(), context.sourceSnapshot().snapshotRef(), "VALIDATED", "REJECTED", false,
                    CrossTrackIntegrationFailure.QUALITY_VERDICT_REJECTED, true, false));
        }
        if (verdict.overallStatus() == CrossTrackQualityVerdictStatus.INCONCLUSIVE) {
            return List.of(CheckFactory.skipped(order, "quality.verdict.status", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                    verdict.verdictRef(), context.sourceSnapshot().snapshotRef(), CrossTrackIntegrationFailure.QUALITY_VERDICT_INCONCLUSIVE, true));
        }
        return List.of(CheckFactory.check(order, "quality.verdict.status", CrossTrackIntegrationScope.QUALITY_VERDICT_BOUNDARY,
                verdict.verdictRef(), context.sourceSnapshot().snapshotRef(), "VALIDATED", verdict.overallStatus().name(), true,
                CrossTrackIntegrationFailure.RECOMMENDATION_QUALITY_VERDICT_INVALID, true, false));
    }
}
