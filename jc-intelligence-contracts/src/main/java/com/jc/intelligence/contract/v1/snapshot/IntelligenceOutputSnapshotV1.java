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
import java.util.List;

public record IntelligenceOutputSnapshotV1(
        ContractId contractVersion,
        SnapshotRef snapshotId,
        SchemaVersion schemaVersion,
        List<EntityRef> resultRefs,
        List<String> explanationRefs,
        String constraintResultRef,
        String fallbackCode,
        String domainExtensionRef,
        String contentHash,
        ProducerBuildId producerBuildId) {

    public IntelligenceOutputSnapshotV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_OUTPUT_SNAPSHOT.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceOutputSnapshotV1 requires intelligence-output-snapshot-v1");
        }
        java.util.Objects.requireNonNull(snapshotId, "snapshotId");
        java.util.Objects.requireNonNull(schemaVersion, "schemaVersion");
        resultRefs = ImmutableCollections.orderedCopy(resultRefs, "resultRefs");
        explanationRefs = ImmutableCollections.orderedCopy(explanationRefs, "explanationRefs");
        for (String explanationRef : explanationRefs) {
            ContractChecks.requireSimpleRef(explanationRef, "explanationRef");
        }
        if (constraintResultRef != null) {
            constraintResultRef = ContractChecks.requireSimpleRef(
                    constraintResultRef,
                    "constraintResultRef");
        }
        if (fallbackCode != null) {
            fallbackCode = ContractChecks.requireReasonCode(fallbackCode, "fallbackCode");
        }
        if (domainExtensionRef != null) {
            domainExtensionRef = ContractChecks.requireSimpleRef(
                    domainExtensionRef,
                    "domainExtensionRef");
        }
        contentHash = ContractChecks.requireHash(contentHash, "contentHash");
        java.util.Objects.requireNonNull(producerBuildId, "producerBuildId");
    }
}
