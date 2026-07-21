package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.command.ClientEventCommandV1;
import com.jc.data.contract.v1.event.EventDefinitionV1;
import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.identity.References;
import java.util.ArrayList;
import java.util.List;

public final class ClientEventCommandValidatorV1 {
    public ValidationResult<ClientEventCommandV1> validate(ClientEventCommandV1 command) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        if (command == null) {
            return ValidationResult.invalid(List.of(
                    new ValidationError(DataValidationErrorCode.INVALID_COMMAND, "command", "command required")));
        }
        EventType eventType = null;
        if (!DataContractValidatorsV1.isLowerSnakeCase(command.requestedEventType())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_EVENT_TYPE, "requestedEventType", "lowercase snake_case required"));
        } else {
            eventType = EventType.fromWire(command.requestedEventType()).orElse(null);
            if (eventType == null) {
                errors.add(new ValidationError(
                        DataValidationErrorCode.UNSUPPORTED_REQUIRED_ENUM, "requestedEventType", "unknown event type"));
            }
        }
        DataContractValidatorsV1.parseCanonicalUtcInstant(command.occurredAt(), "occurredAt")
                .errors().forEach(errors::add);
        try {
            new References.IdempotencyKey(command.idempotencyKey());
        } catch (RuntimeException exception) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.IDEMPOTENCY_KEY_INVALID, "idempotencyKey", "invalid key"));
        }
        if (command.sessionToken() != null
                && (command.sessionToken().isBlank() || command.sessionToken().length() > 256
                || containsWhitespace(command.sessionToken()))) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_COMMAND, "sessionToken", "opaque bounded token required"));
        }
        EventDefinitionV1 definition = eventType == null ? null
                : EventTaxonomyRegistryV1.definition(EventFamily.USER_BEHAVIOR, eventType).orElse(null);
        if (definition != null && definition.entityRequired()) {
            if (command.entityCandidateRef() == null) {
                errors.add(new ValidationError(
                        DataValidationErrorCode.REQUIRED_FIELD_MISSING, "entityCandidateRef", "entity required"));
            } else {
                validateEntity(command.entityCandidateRef(), errors);
            }
        } else if (command.entityCandidateRef() != null) {
            validateEntity(command.entityCandidateRef(), errors);
        }
        // Command context is untrusted intent. Canonical payload requirements may contain
        // server-derived fields and are enforced only on PlatformEventEnvelopeV1.
        errors.addAll(DataContractValidatorsV1.validatePayload(command.context(), null));
        return errors.isEmpty() ? ValidationResult.valid(command) : ValidationResult.invalid(errors);
    }

    private static void validateEntity(String value, List<ValidationError> errors) {
        try {
            new References.EntityRef(value);
        } catch (RuntimeException exception) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_ENTITY_REF, "entityCandidateRef", "invalid entity reference"));
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
