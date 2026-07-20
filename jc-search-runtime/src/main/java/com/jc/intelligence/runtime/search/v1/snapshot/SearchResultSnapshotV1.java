package com.jc.intelligence.runtime.search.v1.snapshot;

import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeAuthorityV1;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SearchResultSnapshotV1(
        SnapshotRef snapshotId,
        String requestFingerprint,
        String queryFingerprint,
        String filterFingerprint,
        PolicyVersion rankingPolicyVersion,
        SchemaVersion retrievalStrategyVersion,
        Instant referenceTime,
        List<SearchResultItemV1> items,
        SchemaVersion runtimeVersion,
        ProducerBuildId producerBuildId,
        Instant builtAt,
        String contentHash,
        SearchRuntimeAuthorityV1 authority) {
    public SearchResultSnapshotV1 {
        Objects.requireNonNull(snapshotId, "snapshotId");
        requestFingerprint = requireHash(requestFingerprint, "requestFingerprint");
        queryFingerprint = requireHash(queryFingerprint, "queryFingerprint");
        filterFingerprint = requireHash(filterFingerprint, "filterFingerprint");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        Objects.requireNonNull(referenceTime, "referenceTime");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (new HashSet<>(items.stream().map(item -> item.candidate().entityRef()).toList()).size() != items.size()) {
            throw new IllegalArgumentException("snapshot items contain duplicate entityRef");
        }
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).finalPosition() != index + 1) {
                throw new IllegalArgumentException("snapshot final positions must be contiguous and 1-based");
            }
        }
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        Objects.requireNonNull(builtAt, "builtAt");
        if (builtAt.isBefore(referenceTime)) throw new IllegalArgumentException("builtAt cannot precede referenceTime");
        contentHash = requireHash(contentHash, "contentHash");
        Objects.requireNonNull(authority, "authority");
    }

    private static String requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }
}
