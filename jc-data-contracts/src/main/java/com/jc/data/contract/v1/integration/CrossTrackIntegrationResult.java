package com.jc.data.contract.v1.integration;

import java.util.Comparator;
import java.util.List;

public record CrossTrackIntegrationResult(
        CrossTrackIntegrationDefinition definition,
        List<CrossTrackIntegrationCheck> checks,
        CrossTrackIntegrationVerdict verdict,
        String integrationInputFingerprint,
        String integrationMappingFingerprint,
        String contractMatrixFingerprint) {
    public CrossTrackIntegrationResult {
        if (definition == null || verdict == null) throw new NullPointerException("result required field");
        checks = List.copyOf(checks.stream().sorted(Comparator.comparingInt(CrossTrackIntegrationCheck::order)
                .thenComparing(CrossTrackIntegrationCheck::checkCode)).toList());
        integrationInputFingerprint = IntegrationSupport.fingerprint(integrationInputFingerprint, "integrationInputFingerprint");
        integrationMappingFingerprint = IntegrationSupport.fingerprint(integrationMappingFingerprint, "integrationMappingFingerprint");
        contractMatrixFingerprint = IntegrationSupport.fingerprint(contractMatrixFingerprint, "contractMatrixFingerprint");
    }
}
