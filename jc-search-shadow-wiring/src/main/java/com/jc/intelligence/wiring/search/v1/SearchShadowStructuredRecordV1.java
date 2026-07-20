package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowMismatchCode;
import com.jc.intelligence.integration.search.v1.SearchShadowSeverity;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowWarningCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record SearchShadowStructuredRecordV1(
        ContractId contractVersion,
        String correlationFingerprint,
        SearchShadowWiringMode shadowMode,
        boolean sampled,
        SearchShadowDispatchStatus dispatchStatus,
        SearchShadowStatus integrationStatus,
        SearchShadowComparisonStatus comparisonStatus,
        List<SearchShadowMismatchCode> mismatchCodes,
        List<SearchShadowWarningCode> warningCodes,
        SearchShadowSeverity severity,
        int legacyCount,
        int runtimeCount,
        double topKOverlapRatio,
        String runtimeDurationBucket,
        String comparisonDurationBucket,
        String samplingPolicyVersion,
        String shadowPolicyVersion,
        ProducerBuildId producerBuildId,
        Instant referenceTime,
        SearchShadowWiringAuthorityV1 authority) {
    public SearchShadowStructuredRecordV1 {
        if (!SearchShadowWiringContractIds.STRUCTURED_RECORD.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        if (correlationFingerprint == null || !correlationFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("correlationFingerprint must be SHA-256");
        if (shadowMode == null || dispatchStatus == null || severity == null || producerBuildId == null || referenceTime == null || authority == null) {
            throw new IllegalArgumentException("structured record fields are required");
        }
        mismatchCodes = List.copyOf(new ArrayList<>(mismatchCodes == null ? List.of() : mismatchCodes));
        warningCodes = List.copyOf(new ArrayList<>(warningCodes == null ? List.of() : warningCodes));
        if (mismatchCodes.stream().anyMatch(java.util.Objects::isNull) || warningCodes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("code collections contain null");
        }
        if (legacyCount < 0 || runtimeCount < 0 || !Double.isFinite(topKOverlapRatio) || topKOverlapRatio < 0 || topKOverlapRatio > 1) {
            throw new IllegalArgumentException("metric values are invalid");
        }
        runtimeDurationBucket = safeBucket(runtimeDurationBucket, "runtimeDurationBucket");
        comparisonDurationBucket = safeBucket(comparisonDurationBucket, "comparisonDurationBucket");
        samplingPolicyVersion = safeVersion(samplingPolicyVersion, "samplingPolicyVersion");
        shadowPolicyVersion = safeVersion(shadowPolicyVersion, "shadowPolicyVersion");
        if (!authority.equals(SearchShadowWiringAuthorityV1.legacyOnly())) throw new IllegalArgumentException("structured record has no production authority");
    }
    private static String safeBucket(String value, String field) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException(field + " must be lowercase_snake_case");
        return value;
    }
    private static String safeVersion(String value, String field) {
        if (value == null || !value.matches("[a-z][a-z0-9-]*-v[1-9][0-9]*")) throw new IllegalArgumentException(field + " must be versioned");
        return value;
    }
}
