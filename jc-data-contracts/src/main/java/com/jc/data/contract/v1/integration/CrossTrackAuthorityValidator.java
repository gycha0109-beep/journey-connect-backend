package com.jc.data.contract.v1.integration;

import java.util.ArrayList;
import java.util.List;

public final class CrossTrackAuthorityValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int startOrder) {
        ArrayList<CrossTrackIntegrationCheck> checks = new ArrayList<>();
        int order = startOrder;
        for (CrossTrackAuthorityRule rule : context.authorityRules().stream()
                .sorted(java.util.Comparator.comparing(CrossTrackAuthorityRule::objectName)).toList()) {
            CrossTrackIntegrationFailure failure = null;
            boolean allowed = true;
            if (rule.readAttempted() && !rule.readAllowedTracks().contains(rule.actorTrack())) {
                failure = CrossTrackIntegrationFailure.CROSS_TRACK_READ_AUTHORITY_VIOLATION; allowed = false;
            } else if (rule.writeAttempted() && !rule.writeAllowedTracks().contains(rule.actorTrack())) {
                failure = CrossTrackIntegrationFailure.CROSS_TRACK_WRITE_AUTHORITY_VIOLATION; allowed = false;
            } else if (rule.validationAttempted() && !rule.validationAllowedTracks().contains(rule.actorTrack())) {
                failure = CrossTrackIntegrationFailure.CROSS_TRACK_VALIDATION_AUTHORITY_VIOLATION; allowed = false;
            } else if (rule.productionAttempted() && !rule.productionAuthority().equals(rule.actorTrack())) {
                failure = CrossTrackIntegrationFailure.CROSS_TRACK_PRODUCTION_AUTHORITY_VIOLATION; allowed = false;
            }
            checks.add(CheckFactory.check(order++, "authority." + rule.objectName().replace(' ', '_'),
                    CrossTrackIntegrationScope.AUTHORITY_BOUNDARY, rule.objectName(), rule.owningTrack(), "allowed", allowed ? "allowed" : "denied",
                    allowed, failure == null ? CrossTrackIntegrationFailure.CROSS_TRACK_OBJECT_OWNERSHIP_CONFLICT : failure, true, false));
        }
        return List.copyOf(checks);
    }
}
