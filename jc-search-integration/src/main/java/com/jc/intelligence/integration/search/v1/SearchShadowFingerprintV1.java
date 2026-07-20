package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SearchShadowFingerprintV1 {
    private SearchShadowFingerprintV1() { }


    public static String runtimeInput(SearchRuntimeExecutionRequestV1 request) {
        StringBuilder canonical = new StringBuilder("search-shadow-runtime-input-v1\n")
                .append(request.runId().value()).append('\n')
                .append(request.runtimeVersion().value()).append('\n')
                .append(request.retrievalStrategyVersion().value()).append('\n')
                .append(com.jc.intelligence.runtime.search.v1.snapshot.SearchRuntimeFingerprintV1.request(request.searchRequest())).append('\n')
                .append(request.fallbackPolicyVersion().value()).append('\n')
                .append(request.producerBuildId().value()).append('\n')
                .append(request.startedAt()).append('\n')
                .append(request.completedAt()).append('\n')
                .append(request.maximumCandidateCount()).append('\n')
                .append(request.exactReplayEligible()).append('\n');
        request.retrievalSources().stream().map(com.jc.intelligence.contract.support.WireValue::wireValue)
                .sorted().forEach(value -> canonical.append(value).append('\n'));
        return sha256(canonical.toString());
    }

    public static String runtimeResult(SearchRuntimeResultV1 result) {
        StringBuilder canonical = new StringBuilder("search-shadow-runtime-result-v1\n")
                .append(result.status().wireValue()).append('\n');
        if (result.snapshot() != null) {
            canonical.append(result.snapshot().snapshotId().value()).append('\n')
                    .append(result.snapshot().contentHash()).append('\n');
            for (var item : result.snapshot().items()) {
                canonical.append(item.finalPosition()).append('|')
                        .append(item.candidate().entityRef().value()).append('\n');
            }
        }
        if (result.failure() != null) canonical.append(result.failure().failureCode().wireValue()).append('\n');
        if (result.fallback() != null) canonical.append(result.fallback().fallbackCode().wireValue()).append('\n');
        return sha256(canonical.toString());
    }

    public static String comparison(
            LegacyExploreCompatibilityResult legacy,
            String runtimeInputFingerprint,
            String runtimeResultFingerprint,
            SearchShadowPolicyV1 policy,
            SearchShadowComparisonResultV1 comparison) {
        StringBuilder canonical = new StringBuilder("search-shadow-comparison-v1\n")
                .append(legacy.evidence() == null ? "none" : legacy.evidence().legacyRequestFingerprint()).append('\n')
                .append(legacy.evidence() == null ? "none" : legacy.evidence().legacyResponseFingerprint()).append('\n')
                .append(runtimeInputFingerprint == null ? "none" : runtimeInputFingerprint).append('\n')
                .append(runtimeResultFingerprint == null ? "none" : runtimeResultFingerprint).append('\n')
                .append(policy.shadowPolicyVersion().value()).append('\n')
                .append(policy.comparisonPolicyVersion().value()).append('\n')
                .append(comparison.status().wireValue()).append('\n');
        SearchShadowComparisonMetricsV1 metrics = comparison.metrics();
        canonical.append(metrics.legacyCount()).append('|').append(metrics.runtimeCount()).append('|')
                .append(metrics.intersectionCount()).append('|').append(metrics.legacyOnlyCount()).append('|')
                .append(metrics.runtimeOnlyCount()).append('|').append(metrics.topKOverlapCount()).append('|')
                .append(Double.toHexString(metrics.topKOverlapRatio())).append('|')
                .append(metrics.sameOrderPrefixLength()).append('|').append(metrics.duplicateCount()).append('\n');
        for (SearchShadowMismatchV1 mismatch : comparison.mismatches()) {
            canonical.append(mismatch.code().wireValue()).append('|')
                    .append(mismatch.severity().wireValue()).append('|')
                    .append(mismatch.entityRef() == null ? "" : mismatch.entityRef()).append('|')
                    .append(mismatch.legacyPosition() == null ? "" : mismatch.legacyPosition()).append('|')
                    .append(mismatch.runtimePosition() == null ? "" : mismatch.runtimePosition()).append('\n');
        }
        return sha256(canonical.toString());
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
