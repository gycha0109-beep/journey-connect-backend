package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class CrossTrackRetentionValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        CrossTrackRetentionRule rule = context.retentionRule();
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        checks.add(CheckFactory.check(startOrder, "retention.target_not_longer", CrossTrackIntegrationScope.RETENTION_BOUNDARY,
                Integer.toString(rule.sourceRetentionDays()), Integer.toString(rule.targetRetentionDays()), "target <= source",
                rule.targetRetentionDays() + " <= " + rule.sourceRetentionDays(), rule.targetRetentionDays() <= rule.sourceRetentionDays(),
                CrossTrackIntegrationFailure.CROSS_TRACK_RETENTION_CONFLICT, true, false));
        checks.add(CheckFactory.check(startOrder + 1, "retention.deletion_semantics", CrossTrackIntegrationScope.RETENTION_BOUNDARY,
                "source deletion", "target deletion", "aligned", Boolean.toString(rule.deletionSemanticsAligned()), rule.deletionSemanticsAligned(),
                CrossTrackIntegrationFailure.CROSS_TRACK_DELETION_SEMANTIC_CONFLICT, true, false));
        checks.add(CheckFactory.check(startOrder + 2, "retention.no_automatic_purge", CrossTrackIntegrationScope.RETENTION_BOUNDARY,
                "integration evidence", "purge", "disabled", Boolean.toString(rule.automaticPurgeEnabled()), !rule.automaticPurgeEnabled(),
                CrossTrackIntegrationFailure.CROSS_TRACK_RETENTION_CONFLICT, true, false));
        checks.add(CheckFactory.check(startOrder + 3, "retention.no_physical_delete", CrossTrackIntegrationScope.RETENTION_BOUNDARY,
                "integration evidence", "physical delete", "disabled", Boolean.toString(rule.physicalDeleteEnabled()), !rule.physicalDeleteEnabled(),
                CrossTrackIntegrationFailure.CROSS_TRACK_DELETION_SEMANTIC_CONFLICT, true, false));
        return List.copyOf(checks);
    }
}
