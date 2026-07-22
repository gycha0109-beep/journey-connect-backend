package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.v1.version.Versions;
import java.util.List;
import java.util.Objects;

public record RecommendationP0AdapterOutputV1(
        String adapterId,
        String adapterVersion,
        String mappingPolicyVersion,
        String outputFingerprintVersion,
        String sourceEventRef,
        String sourceFingerprint,
        String targetContractVersion,
        String targetSchemaVersion,
        RecommendationP0CompatibilityClass compatibilityClass,
        RecommendationP0MappingStatus mappingStatus,
        RecommendationP0MappedEventV1 mappedEvent,
        String outputFingerprint,
        RecommendationP0MappingFailure failure,
        Versions.ProducerBuildId producerBuildId,
        List<String> droppedMetadataKeys) {
    public RecommendationP0AdapterOutputV1 {
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(adapterVersion, "adapterVersion");
        Objects.requireNonNull(mappingPolicyVersion, "mappingPolicyVersion");
        Objects.requireNonNull(outputFingerprintVersion, "outputFingerprintVersion");
        Objects.requireNonNull(compatibilityClass, "compatibilityClass");
        Objects.requireNonNull(mappingStatus, "mappingStatus");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        droppedMetadataKeys = List.copyOf(droppedMetadataKeys);
        boolean mapped = mappingStatus == RecommendationP0MappingStatus.MAPPED_SHADOW;
        if (mapped != (mappedEvent != null && outputFingerprint != null && failure == null)) {
            throw new IllegalArgumentException("adapter output state mismatch");
        }
        if (!mapped && (mappedEvent != null || outputFingerprint != null || failure == null)) {
            throw new IllegalArgumentException("failed adapter output state mismatch");
        }
    }

    public boolean isMapped() {
        return mappingStatus == RecommendationP0MappingStatus.MAPPED_SHADOW;
    }
}
