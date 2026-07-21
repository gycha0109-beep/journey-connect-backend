package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.event.EventDefinitionV1;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.PlatformEventEnvelopeV1;
import com.jc.data.contract.v1.identity.IdentityScheme;
import java.util.ArrayList;
import java.util.List;

public final class PlatformEventEnvelopeValidatorV1 {
    public static final String CONTRACT_VERSION = "platform-event-v1";
    public static final String SCHEMA_VERSION = "user-behavior-event-v1";
    public static final String CANONICALIZATION_VERSION = "platform-event-canonical-json-v1";

    public ValidationResult<PlatformEventEnvelopeV1> validate(PlatformEventEnvelopeV1 event) {
        if (event == null) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.REQUIRED_FIELD_MISSING, "event", "event required")));
        }
        ArrayList<ValidationError> errors = new ArrayList<>();
        require(event.contractVersion(), "contractVersion", errors);
        require(event.schemaVersion(), "schemaVersion", errors);
        require(event.canonicalizationVersion(), "canonicalizationVersion", errors);
        require(event.producerVersion(), "producerVersion", errors);
        require(event.producerBuildId(), "producerBuildId", errors);
        require(event.eventId(), "eventId", errors);
        require(event.eventFamily(), "eventFamily", errors);
        require(event.eventType(), "eventType", errors);
        require(event.occurredAt(), "occurredAt", errors);
        require(event.receivedAt(), "receivedAt", errors);
        require(event.actorRef(), "actorRef", errors);
        require(event.sessionRef(), "sessionRef", errors);
        require(event.requestId(), "requestId", errors);
        require(event.correlationId(), "correlationId", errors);
        require(event.idempotencyKey(), "idempotencyKey", errors);

        if (event.contractVersion() != null && !CONTRACT_VERSION.equals(event.contractVersion().value())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_CONTRACT_VERSION, "contractVersion", "unsupported contract"));
        }
        if (event.schemaVersion() != null && !SCHEMA_VERSION.equals(event.schemaVersion().value())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_SCHEMA_VERSION, "schemaVersion", "unsupported schema"));
        }
        if (event.canonicalizationVersion() != null
                && !CANONICALIZATION_VERSION.equals(event.canonicalizationVersion().value())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                    "canonicalizationVersion", "unsupported canonicalization"));
        }
        if (event.actorRef() != null
                && event.actorRef().subject().scheme() != IdentityScheme.PLATFORM_SUBJECT_V1) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.IDENTITY_NAMESPACE_MISMATCH, "actorRef", "platform subject required"));
        }
        if (event.occurredAt() != null && event.receivedAt() != null
                && event.receivedAt().isBefore(event.occurredAt())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_TIMESTAMP, "receivedAt", "cannot precede occurredAt"));
        }
        EventDefinitionV1 definition = null;
        if (event.eventFamily() != null && event.eventType() != null) {
            definition = EventTaxonomyRegistryV1.definition(event.eventFamily(), event.eventType()).orElse(null);
            if (definition == null) {
                errors.add(new ValidationError(
                        DataValidationErrorCode.INVALID_FAMILY_TYPE_COMBINATION,
                        "eventFamily/eventType", "unsupported family/type combination"));
            }
        }
        if (definition != null && definition.entityRequired() && event.entityRef() == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.REQUIRED_FIELD_MISSING, "entityRef", "entity required"));
        }
        errors.addAll(DataContractValidatorsV1.validatePayload(event.payload(), definition));
        return errors.isEmpty() ? ValidationResult.valid(event) : ValidationResult.invalid(errors);
    }

    private static void require(Object value, String field, List<ValidationError> errors) {
        if (value == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.REQUIRED_FIELD_MISSING, field, "required"));
        }
    }
}
