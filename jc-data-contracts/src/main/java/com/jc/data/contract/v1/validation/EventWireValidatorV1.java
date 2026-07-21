package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.EventType;
import java.util.List;

public final class EventWireValidatorV1 {
    private EventWireValidatorV1() {
    }

    public static ValidationResult<EventFamily> family(String wireValue) {
        if (!DataContractValidatorsV1.isLowerSnakeCase(wireValue)) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.INVALID_EVENT_FAMILY,
                    "eventFamily", "lowercase snake_case required")));
        }
        return EventFamily.fromWire(wireValue)
                .map(ValidationResult::valid)
                .orElseGet(() -> ValidationResult.invalid(List.of(new ValidationError(
                        DataValidationErrorCode.UNSUPPORTED_REQUIRED_ENUM,
                        "eventFamily", "unknown required event family"))));
    }

    public static ValidationResult<EventType> type(String wireValue) {
        if (!DataContractValidatorsV1.isLowerSnakeCase(wireValue)) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.INVALID_EVENT_TYPE,
                    "eventType", "lowercase snake_case required")));
        }
        return EventType.fromWire(wireValue)
                .map(ValidationResult::valid)
                .orElseGet(() -> ValidationResult.invalid(List.of(new ValidationError(
                        DataValidationErrorCode.UNSUPPORTED_REQUIRED_ENUM,
                        "eventType", "unknown required event type"))));
    }

    public static ValidationResult<EventType> familyType(EventFamily family, EventType type) {
        if (family == null || type == null || EventTaxonomyRegistryV1.definition(family, type).isEmpty()) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.INVALID_FAMILY_TYPE_COMBINATION,
                    "eventFamily/eventType", "unsupported family/type combination")));
        }
        return ValidationResult.valid(type);
    }
}
