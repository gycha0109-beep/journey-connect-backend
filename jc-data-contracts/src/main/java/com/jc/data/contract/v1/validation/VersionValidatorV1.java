package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.version.Versions;
import java.util.List;
import java.util.function.Function;

public final class VersionValidatorV1 {
    private VersionValidatorV1() {
    }

    public static ValidationResult<String> contractVersion(String value) {
        return validate(value, "contractVersion", DataValidationErrorCode.INVALID_CONTRACT_VERSION,
                Versions.ContractVersion::new);
    }

    public static ValidationResult<String> schemaVersion(String value) {
        return validate(value, "schemaVersion", DataValidationErrorCode.INVALID_SCHEMA_VERSION,
                Versions.SchemaVersion::new);
    }

    public static ValidationResult<String> canonicalizationVersion(String value) {
        return validate(value, "canonicalizationVersion",
                DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                Versions.CanonicalizationVersion::new);
    }

    public static ValidationResult<String> producerVersion(String value) {
        return validate(value, "producerVersion", DataValidationErrorCode.INVALID_PRODUCER_VERSION,
                Versions.ProducerVersion::new);
    }

    public static ValidationResult<String> consumerVersion(String value) {
        return validate(value, "consumerVersion", DataValidationErrorCode.INVALID_CONSUMER_VERSION,
                Versions.ConsumerVersion::new);
    }

    public static ValidationResult<String> producerBuildId(String value) {
        return validate(value, "producerBuildId", DataValidationErrorCode.INVALID_PRODUCER_BUILD_ID,
                Versions.ProducerBuildId::new);
    }

    private static ValidationResult<String> validate(
            String value,
            String field,
            DataValidationErrorCode code,
            Function<String, ?> constructor) {
        try {
            constructor.apply(value);
            return ValidationResult.valid(value);
        } catch (RuntimeException exception) {
            return ValidationResult.invalid(List.of(new ValidationError(code, field, "invalid version value")));
        }
    }
}
