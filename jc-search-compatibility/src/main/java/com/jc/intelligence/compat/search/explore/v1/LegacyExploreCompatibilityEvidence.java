package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record LegacyExploreCompatibilityEvidence(
        ContractId adapterVersion, String legacyEndpointId, String legacyRequestFingerprint,
        String legacyResponseFingerprint, PolicyVersion mappingPolicyVersion, Instant mappedAt,
        ProducerBuildId producerBuildId, int sourceItemCount, int mappedItemCount, int rejectedItemCount,
        List<LegacyExploreWarningCode> warningCodes, boolean runtimeAuthority, boolean persistenceAuthority,
        boolean replayAuthority, boolean exposureAuthority) {
    public LegacyExploreCompatibilityEvidence {
        if (!LegacyExploreContractIds.ADAPTER.equals(adapterVersion)) {
            throw new IllegalArgumentException("unexpected adapterVersion");
        }
        if (!LegacyExploreContractIds.ENDPOINT_ID.equals(legacyEndpointId)) {
            throw new IllegalArgumentException("unexpected legacyEndpointId");
        }
        if (legacyRequestFingerprint == null || !legacyRequestFingerprint.matches("[0-9a-f]{64}")
                || legacyResponseFingerprint == null || !legacyResponseFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("compatibility fingerprints must be lowercase SHA-256");
        }
        if (!LegacyExploreContractIds.MAPPING_POLICY.equals(mappingPolicyVersion)) {
            throw new IllegalArgumentException("unexpected mappingPolicyVersion");
        }
        java.util.Objects.requireNonNull(mappedAt, "mappedAt");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
        warningCodes = List.copyOf(new ArrayList<>(java.util.Objects.requireNonNull(warningCodes, "warningCodes")));
        if (warningCodes.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.LinkedHashSet<>(warningCodes).size() != warningCodes.size()) {
            throw new IllegalArgumentException("warningCodes must be non-null and unique");
        }
        if (runtimeAuthority || persistenceAuthority || replayAuthority || exposureAuthority) {
            throw new IllegalArgumentException("compatibility evidence cannot activate runtime/persistence/replay/exposure authority");
        }
        if (sourceItemCount < 0 || mappedItemCount < 0 || rejectedItemCount < 0
                || mappedItemCount + rejectedItemCount > sourceItemCount) {
            throw new IllegalArgumentException("invalid mapping counts");
        }
    }
}
