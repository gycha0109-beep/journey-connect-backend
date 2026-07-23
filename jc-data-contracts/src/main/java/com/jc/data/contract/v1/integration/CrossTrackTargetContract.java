package com.jc.data.contract.v1.integration;

import java.util.List;
import java.util.Map;

public record CrossTrackTargetContract(
        String targetTrack,
        String contractId,
        String schemaVersion,
        boolean present,
        boolean authorityConfirmed,
        boolean domainMappingApproved,
        String identityNamespace,
        String privacyClass,
        int retentionDays,
        List<String> requiredFields,
        Map<String, String> semanticUnits) {
    public CrossTrackTargetContract {
        targetTrack = IntegrationSupport.text(targetTrack, "targetTrack");
        contractId = IntegrationSupport.version(contractId, "contractId");
        schemaVersion = IntegrationSupport.version(schemaVersion, "schemaVersion");
        identityNamespace = identityNamespace == null ? "" : identityNamespace;
        privacyClass = IntegrationSupport.text(privacyClass, "privacyClass");
        if (retentionDays < 1) throw new IllegalArgumentException("retentionDays");
        requiredFields = IntegrationSupport.list(requiredFields, "requiredFields");
        semanticUnits = IntegrationSupport.map(semanticUnits, "semanticUnits");
    }
}
