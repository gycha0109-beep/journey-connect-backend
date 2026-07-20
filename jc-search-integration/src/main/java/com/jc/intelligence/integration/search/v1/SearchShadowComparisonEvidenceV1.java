package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record SearchShadowComparisonEvidenceV1(
        ContractId contractVersion,
        String comparisonId,
        String correlationId,
        SearchShadowMode shadowMode,
        PolicyVersion shadowPolicyVersion,
        String legacyEndpointId,
        String legacyRequestFingerprint,
        String legacyResponseFingerprint,
        String runtimeInputFingerprint,
        String runtimeResultFingerprint,
        PolicyVersion comparisonPolicyVersion,
        Instant referenceTime,
        SearchShadowStatus integrationStatus,
        SearchShadowRuntimeInputStatus runtimeInputStatus,
        String runtimeStatus,
        SearchShadowComparisonStatus comparisonStatus,
        SearchShadowComparisonMetricsV1 metrics,
        List<SearchShadowMismatchV1> mismatches,
        List<SearchShadowWarningCode> warningCodes,
        SearchShadowSeverity severity,
        ProducerBuildId producerBuildId,
        SearchShadowAuthorityV1 authority) {
    public SearchShadowComparisonEvidenceV1 {
        if (!SearchIntegrationContractIds.COMPARISON_EVIDENCE.equals(contractVersion)) {
            throw new IllegalArgumentException("unexpected comparison evidence contractVersion");
        }
        comparisonId = requireSafeId(comparisonId, "comparisonId");
        correlationId = requireSafeId(correlationId, "correlationId");
        if (shadowMode == null || shadowPolicyVersion == null || comparisonPolicyVersion == null
                || referenceTime == null || integrationStatus == null || runtimeInputStatus == null
                || comparisonStatus == null || metrics == null || severity == null || producerBuildId == null
                || authority == null) {
            throw new IllegalArgumentException("comparison evidence fields are required");
        }
        legacyEndpointId = requireSafeId(legacyEndpointId, "legacyEndpointId");
        legacyRequestFingerprint = requireHash(legacyRequestFingerprint, "legacyRequestFingerprint");
        legacyResponseFingerprint = requireHash(legacyResponseFingerprint, "legacyResponseFingerprint");
        if (runtimeInputFingerprint != null) runtimeInputFingerprint = requireHash(runtimeInputFingerprint, "runtimeInputFingerprint");
        if (runtimeResultFingerprint != null) runtimeResultFingerprint = requireHash(runtimeResultFingerprint, "runtimeResultFingerprint");
        if (runtimeStatus != null && !runtimeStatus.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("runtimeStatus must be lowercase_snake_case");
        }
        mismatches = List.copyOf(new ArrayList<>(mismatches == null ? List.of() : mismatches));
        warningCodes = List.copyOf(new ArrayList<>(warningCodes == null ? List.of() : warningCodes));
        if (mismatches.stream().anyMatch(java.util.Objects::isNull)
                || warningCodes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("evidence collections contain null");
        }
        if (!authority.equals(SearchShadowAuthorityV1.legacyOnly())) {
            throw new IllegalArgumentException("comparison evidence must remain non-authoritative");
        }
    }

    private static String requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        return value;
    }
    private static String requireSafeId(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 200
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " must be safe trimmed text");
        }
        return value;
    }
}
