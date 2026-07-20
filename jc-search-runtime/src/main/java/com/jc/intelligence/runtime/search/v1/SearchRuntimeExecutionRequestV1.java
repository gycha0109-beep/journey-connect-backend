package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.Objects;

public record SearchRuntimeExecutionRequestV1(
        SearchRequestV1 searchRequest,
        RunRef runId,
        SchemaVersion runtimeVersion,
        SchemaVersion retrievalStrategyVersion,
        java.util.List<RetrievalSource> retrievalSources,
        PolicyVersion fallbackPolicyVersion,
        ProducerBuildId producerBuildId,
        Instant startedAt,
        Instant completedAt,
        int maximumCandidateCount,
        boolean exactReplayEligible) {
    public SearchRuntimeExecutionRequestV1 {
        Objects.requireNonNull(searchRequest, "searchRequest");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        if (!"search-runtime-foundation-v1".equals(runtimeVersion.value())) {
            throw new IllegalArgumentException("runtimeVersion must be search-runtime-foundation-v1");
        }
        Objects.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        retrievalSources = java.util.List.copyOf(Objects.requireNonNull(retrievalSources, "retrievalSources"));
        if (retrievalSources.isEmpty()) throw new IllegalArgumentException("retrievalSources are required");
        if (exactReplayEligible && retrievalSources.contains(RetrievalSource.EXTERNAL_PROVIDER)) {
            throw new IllegalArgumentException("external provider retrieval cannot declare exact replay in IP-5");
        }
        Objects.requireNonNull(fallbackPolicyVersion, "fallbackPolicyVersion");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt cannot precede startedAt");
        if (maximumCandidateCount < 1 || maximumCandidateCount > 1000) {
            throw new IllegalArgumentException("maximumCandidateCount must be 1..1000");
        }
        if (!searchRequest.rankingPolicyVersion().equals(searchRequest.sort().sortPolicyVersion())) {
            throw new IllegalArgumentException("foundation runtime requires ranking and sort policy versions to match");
        }
        if (searchRequest.pageRequest().cursor() != null) {
            throw new IllegalArgumentException("IP-5 cannot resume a persisted snapshot cursor");
        }
    }
}
