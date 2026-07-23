package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class CrossTrackPrivacyValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        CrossTrackPrivacyRule rule = context.privacyRule();
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        checks.add(CheckFactory.check(startOrder, "privacy.raw_payload", CrossTrackIntegrationScope.PRIVACY_BOUNDARY,
                rule.sourcePrivacyClass(), rule.targetPrivacyClass(), "absent", Boolean.toString(rule.rawPayloadPresent()), !rule.rawPayloadPresent(),
                CrossTrackIntegrationFailure.CROSS_TRACK_RAW_PAYLOAD_EXPOSURE, true, false));
        checks.add(CheckFactory.check(startOrder + 1, "privacy.pii", CrossTrackIntegrationScope.PRIVACY_BOUNDARY,
                rule.sourcePrivacyClass(), rule.targetPrivacyClass(), "absent", Boolean.toString(rule.piiPresent()), !rule.piiPresent(),
                CrossTrackIntegrationFailure.CROSS_TRACK_PII_EXPOSURE, true, false));
        checks.add(CheckFactory.check(startOrder + 2, "privacy.precise_location", CrossTrackIntegrationScope.PRIVACY_BOUNDARY,
                rule.sourcePrivacyClass(), rule.targetPrivacyClass(), "absent", Boolean.toString(rule.preciseLocationPresent()), !rule.preciseLocationPresent(),
                CrossTrackIntegrationFailure.CROSS_TRACK_PRECISE_LOCATION_EXPOSURE, true, false));
        checks.add(CheckFactory.check(startOrder + 3, "privacy.lineage_purpose", CrossTrackIntegrationScope.PRIVACY_BOUNDARY,
                "lineage", "purpose binding", "true", Boolean.toString(rule.lineagePurposeBound()), rule.lineagePurposeBound(),
                CrossTrackIntegrationFailure.CROSS_TRACK_LINEAGE_ACCESS_VIOLATION, true, false));
        checks.add(CheckFactory.check(startOrder + 4, "privacy.reidentification", CrossTrackIntegrationScope.PRIVACY_BOUNDARY,
                "safe aggregate view", "reader", "false", Boolean.toString(rule.reidentificationRisk()), !rule.reidentificationRisk(),
                CrossTrackIntegrationFailure.CROSS_TRACK_REIDENTIFICATION_RISK, true, false));
        return List.copyOf(checks);
    }
}
