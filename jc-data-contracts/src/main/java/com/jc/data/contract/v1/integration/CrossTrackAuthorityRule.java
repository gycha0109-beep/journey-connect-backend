package com.jc.data.contract.v1.integration;

import java.util.Set;

public record CrossTrackAuthorityRule(
        String objectName,
        String owningTrack,
        Set<String> readAllowedTracks,
        Set<String> writeAllowedTracks,
        Set<String> validationAllowedTracks,
        String productionAuthority,
        String actorTrack,
        boolean readAttempted,
        boolean writeAttempted,
        boolean validationAttempted,
        boolean productionAttempted) {
    public CrossTrackAuthorityRule {
        objectName = IntegrationSupport.text(objectName, "objectName");
        owningTrack = IntegrationSupport.text(owningTrack, "owningTrack");
        readAllowedTracks = Set.copyOf(readAllowedTracks);
        writeAllowedTracks = Set.copyOf(writeAllowedTracks);
        validationAllowedTracks = Set.copyOf(validationAllowedTracks);
        productionAuthority = IntegrationSupport.text(productionAuthority, "productionAuthority");
        actorTrack = IntegrationSupport.text(actorTrack, "actorTrack");
    }
}
