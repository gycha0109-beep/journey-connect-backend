package com.jc.intelligence.contract.v1.snapshot;

import com.jc.intelligence.contract.support.ImmutableCollections;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.List;

public record IntelligenceInputSnapshotV1(
        ContractId contractVersion,
        SnapshotRef snapshotId,
        SchemaVersion schemaVersion,
        List<String> sourceRefs,
        String identityContextRef,
        Instant referenceTime,
        SchemaVersion canonicalizationVersion,
        String contentHash,
        long payloadSizeBytes,
        PrivacyClass privacyClass,
        ProducerBuildId producerBuildId) {

    public IntelligenceInputSnapshotV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_INPUT_SNAPSHOT.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceInputSnapshotV1 requires intelligence-input-snapshot-v1");
        }
        java.util.Objects.requireNonNull(snapshotId, "snapshotId");
        java.util.Objects.requireNonNull(schemaVersion, "schemaVersion");
        sourceRefs = ImmutableCollections.orderedCopy(sourceRefs, "sourceRefs");
        if (sourceRefs.isEmpty()) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_SNAPSHOT_INVALID,
                    "input snapshot requires at least one sourceRef");
        }
        for (String sourceRef : sourceRefs) {
            ContractChecks.requireSimpleRef(sourceRef, "sourceRef");
        }
        if (identityContextRef != null) {
            identityContextRef = ContractChecks.requireSimpleRef(
                    identityContextRef,
                    "identityContextRef");
        }
        ContractChecks.requireInstant(referenceTime, "referenceTime");
        java.util.Objects.requireNonNull(canonicalizationVersion, "canonicalizationVersion");
        contentHash = ContractChecks.requireHash(contentHash, "contentHash");
        if (payloadSizeBytes < 0L) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_SNAPSHOT_INVALID,
                    "payloadSizeBytes must be nonnegative");
        }
        java.util.Objects.requireNonNull(privacyClass, "privacyClass");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
    }
}
