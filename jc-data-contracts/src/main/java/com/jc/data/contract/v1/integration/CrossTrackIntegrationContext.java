package com.jc.data.contract.v1.integration;

import java.time.Instant;
import java.util.List;

public record CrossTrackIntegrationContext(
        CrossTrackIntegrationDefinition definition,
        CrossTrackSourceSnapshot sourceSnapshot,
        CrossTrackQualityVerdictEvidence qualityVerdict,
        CrossTrackTargetContract targetContract,
        CrossTrackContractMapping contractMapping,
        CrossTrackIdentityBinding identityBinding,
        List<CrossTrackAuthorityRule> authorityRules,
        CrossTrackPrivacyRule privacyRule,
        CrossTrackRetentionRule retentionRule,
        CrossTrackIntegrationPolicy integrationPolicy,
        Instant validationAsOf,
        String expectedSnapshotFingerprint,
        String expectedLineageFingerprint,
        String expectedQualityVerdictFingerprint,
        boolean recommendationProductionWriteAttempted,
        boolean intelligenceRuntimeActivationAttempted,
        boolean searchIndexWriteAttempted,
        boolean searchCutoverAttempted,
        boolean searchDocumentMappingAttempted) {
    public CrossTrackIntegrationContext {
        if (definition == null || sourceSnapshot == null || targetContract == null || contractMapping == null
                || privacyRule == null || retentionRule == null || integrationPolicy == null) {
            throw new NullPointerException("integration context required field");
        }
        authorityRules = IntegrationSupport.list(authorityRules, "authorityRules");
        validationAsOf = IntegrationSupport.instant(validationAsOf, "validationAsOf");
        expectedSnapshotFingerprint = IntegrationSupport.fingerprint(expectedSnapshotFingerprint, "expectedSnapshotFingerprint");
        expectedLineageFingerprint = IntegrationSupport.fingerprint(expectedLineageFingerprint, "expectedLineageFingerprint");
        expectedQualityVerdictFingerprint = IntegrationSupport.fingerprint(expectedQualityVerdictFingerprint, "expectedQualityVerdictFingerprint");
    }
}
