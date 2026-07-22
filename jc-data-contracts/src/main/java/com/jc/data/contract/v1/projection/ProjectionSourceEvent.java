package com.jc.data.contract.v1.projection;

import com.jc.data.contract.support.ImmutableContractValuesV1;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProjectionSourceEvent(
        String sourceEventRef,
        String sourceFingerprint,
        String sourceContractVersion,
        String sourceSchemaVersion,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String identityRef,
        String sessionRef,
        String entityRef,
        String regionRef,
        String contentRef,
        List<String> tagRefs,
        String exposureRef,
        String variantRef,
        AdapterEvidenceState adapterEvidenceState,
        String adapterEvidenceRef,
        String mappingPolicyVersion,
        Map<String, Object> attributes,
        String sourceCanonicalForm) {

    public ProjectionSourceEvent {
        sourceEventRef = ProjectionEngineSupport.requireReference(sourceEventRef, "sourceEventRef");
        sourceFingerprint = ProjectionEngineSupport.requireFingerprint(sourceFingerprint, "sourceFingerprint");
        sourceContractVersion = ProjectionEngineSupport.requireVersion(
                sourceContractVersion, "sourceContractVersion");
        sourceSchemaVersion = ProjectionEngineSupport.requireVersion(sourceSchemaVersion, "sourceSchemaVersion");
        eventType = ProjectionEngineSupport.requireToken(eventType, "eventType", 80);
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        identityRef = ProjectionEngineSupport.requireIdentity(identityRef, "identityRef");
        if (sessionRef != null) {
            sessionRef = ProjectionEngineSupport.requireReference(sessionRef, "sessionRef");
        }
        if (entityRef != null) {
            entityRef = ProjectionEngineSupport.requireReference(entityRef, "entityRef");
        }
        if (regionRef != null) {
            regionRef = ProjectionEngineSupport.requireTypedReference(regionRef, "region", "regionRef");
        }
        if (contentRef != null) {
            contentRef = ProjectionEngineSupport.requireReference(contentRef, "contentRef");
        }
        tagRefs = List.copyOf(Objects.requireNonNull(tagRefs, "tagRefs"));
        for (String tagRef : tagRefs) {
            ProjectionEngineSupport.requireTypedReference(tagRef, "tag", "tagRef");
        }
        if (exposureRef != null) {
            exposureRef = ProjectionEngineSupport.requireReference(exposureRef, "exposureRef");
        }
        if (variantRef != null) {
            variantRef = ProjectionEngineSupport.requireToken(variantRef, "variantRef", 32);
        }
        Objects.requireNonNull(adapterEvidenceState, "adapterEvidenceState");
        if (adapterEvidenceState == AdapterEvidenceState.MAPPED
                || adapterEvidenceState == AdapterEvidenceState.DUPLICATE) {
            adapterEvidenceRef = ProjectionEngineSupport.requireReference(
                    adapterEvidenceRef, "adapterEvidenceRef");
            mappingPolicyVersion = ProjectionEngineSupport.requireVersion(
                    mappingPolicyVersion, "mappingPolicyVersion");
        } else if (adapterEvidenceRef != null || mappingPolicyVersion != null) {
            throw new IllegalArgumentException("non-mapped adapter evidence cannot carry mapped references");
        }
        attributes = ImmutableContractValuesV1.copyMap(Objects.requireNonNull(attributes, "attributes"));
        sourceCanonicalForm = ProjectionEngineSupport.requireCanonicalForm(sourceCanonicalForm);
    }
}
