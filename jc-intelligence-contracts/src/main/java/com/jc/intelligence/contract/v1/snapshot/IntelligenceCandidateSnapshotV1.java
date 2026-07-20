package com.jc.intelligence.contract.v1.snapshot;

import com.jc.intelligence.contract.support.ImmutableCollections;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.util.HashSet;
import java.util.List;

public record IntelligenceCandidateSnapshotV1(
        ContractId contractVersion,
        SnapshotRef snapshotId,
        SchemaVersion schemaVersion,
        List<EntityRef> candidateRefs,
        OrderingSemantics orderingSemantics,
        String domainExtensionRef,
        String contentHash,
        ProducerBuildId producerBuildId) {

    public IntelligenceCandidateSnapshotV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_CANDIDATE_SNAPSHOT.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceCandidateSnapshotV1 requires intelligence-candidate-snapshot-v1");
        }
        java.util.Objects.requireNonNull(snapshotId, "snapshotId");
        java.util.Objects.requireNonNull(schemaVersion, "schemaVersion");
        candidateRefs = ImmutableCollections.orderedCopy(candidateRefs, "candidateRefs");
        if (new HashSet<>(candidateRefs).size() != candidateRefs.size()) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_SNAPSHOT_INVALID,
                    "candidateRefs must not contain duplicates");
        }
        java.util.Objects.requireNonNull(orderingSemantics, "orderingSemantics");
        if (domainExtensionRef != null) {
            domainExtensionRef = ContractChecks.requireSimpleRef(
                    domainExtensionRef,
                    "domainExtensionRef");
        }
        contentHash = ContractChecks.requireHash(contentHash, "contentHash");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
    }
}
