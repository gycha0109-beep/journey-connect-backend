package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class CrossTrackFingerprintValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        checks.add(CheckFactory.check(startOrder, "fingerprint.snapshot", CrossTrackIntegrationScope.FINGERPRINT_BOUNDARY,
                context.sourceSnapshot().snapshotRef(), "snapshot fingerprint", context.expectedSnapshotFingerprint(),
                context.sourceSnapshot().contentFingerprint(), context.expectedSnapshotFingerprint().equals(context.sourceSnapshot().contentFingerprint()),
                CrossTrackIntegrationFailure.CROSS_TRACK_FINGERPRINT_INVALID, true, false));
        checks.add(CheckFactory.check(startOrder + 1, "fingerprint.lineage", CrossTrackIntegrationScope.FINGERPRINT_BOUNDARY,
                context.sourceSnapshot().snapshotRef(), "lineage fingerprint", context.expectedLineageFingerprint(),
                context.sourceSnapshot().lineageFingerprint(), context.expectedLineageFingerprint().equals(context.sourceSnapshot().lineageFingerprint()),
                CrossTrackIntegrationFailure.CROSS_TRACK_FINGERPRINT_INVALID, true, false));
        if (context.qualityVerdict() == null) {
            checks.add(CheckFactory.skipped(startOrder + 2, "fingerprint.quality_verdict",
                    CrossTrackIntegrationScope.FINGERPRINT_BOUNDARY, context.sourceSnapshot().snapshotRef(),
                    "quality verdict fingerprint", CrossTrackIntegrationFailure.QUALITY_VERDICT_MISSING, true));
        } else {
            String observed = context.qualityVerdict().verdictFingerprint();
            boolean valid = context.expectedQualityVerdictFingerprint().equals(observed);
            checks.add(CheckFactory.check(startOrder + 2, "fingerprint.quality_verdict",
                    CrossTrackIntegrationScope.FINGERPRINT_BOUNDARY, context.sourceSnapshot().snapshotRef(),
                    "quality verdict fingerprint", context.expectedQualityVerdictFingerprint(), observed, valid,
                    CrossTrackIntegrationFailure.QUALITY_VERDICT_FINGERPRINT_MISMATCH, true, false));
        }
        return List.copyOf(checks);
    }
}
