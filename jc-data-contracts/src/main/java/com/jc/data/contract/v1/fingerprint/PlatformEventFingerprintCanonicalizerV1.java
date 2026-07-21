package com.jc.data.contract.v1.fingerprint;

import com.jc.data.contract.v1.canonical.CanonicalJsonNormalizerV1;
import com.jc.data.contract.v1.canonical.CanonicalizationRequestV1;
import com.jc.data.contract.v1.canonical.CanonicalizationResultV1;
import com.jc.data.contract.v1.event.PlatformEventEnvelopeV1;
import com.jc.data.contract.v1.validation.PlatformEventEnvelopeValidatorV1;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlatformEventFingerprintCanonicalizerV1 {
    public static final String FINGERPRINT_VERSION = "platform-event-fingerprint-sha256-v1";

    private final PlatformEventEnvelopeValidatorV1 envelopeValidator = new PlatformEventEnvelopeValidatorV1();
    private final CanonicalJsonNormalizerV1 normalizer = new CanonicalJsonNormalizerV1();

    public CanonicalizationResultV1 canonicalize(PlatformEventEnvelopeV1 event) {
        var validation = envelopeValidator.validate(event);
        if (!validation.isValid()) {
            return new CanonicalizationResultV1(null, validation.errors());
        }

        Map<String, Object> approvedFields = new LinkedHashMap<>();
        approvedFields.put("actorRef", event.actorRef() == null ? null : event.actorRef().value());
        approvedFields.put("canonicalizationVersion", event.canonicalizationVersion().value());
        approvedFields.put("causationId", event.causationId() == null ? null : event.causationId().value());
        approvedFields.put("contractVersion", event.contractVersion().value());
        approvedFields.put("entityRef", event.entityRef() == null ? null : event.entityRef().value());
        approvedFields.put("eventFamily", event.eventFamily().wireValue());
        approvedFields.put("eventType", event.eventType().wireValue());
        approvedFields.put("occurredAt", event.occurredAt());
        approvedFields.put("payload", event.payload());
        approvedFields.put("schemaVersion", event.schemaVersion().value());
        approvedFields.put("sessionRef", event.sessionRef() == null ? null : event.sessionRef().value());
        return normalizer.canonicalizePlatformEventFingerprintV1(new CanonicalizationRequestV1(
                event.canonicalizationVersion(), approvedFields));
    }
}
