package com.jc.data.contract.v1.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContractIdValidatorV1 {
    private static final Pattern VERSIONED_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*");
    private static final Set<String> FORBIDDEN = Set.of("latest", "current", "default");

    private ContractIdValidatorV1() {
    }

    public static ValidationResult<String> validate(String value) {
        if (value == null || FORBIDDEN.contains(value) || !VERSIONED_ID.matcher(value).matches()) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.MALFORMED_ID, "contractId", "lowercase versioned contract ID required")));
        }
        return ValidationResult.valid(value);
    }

    public static ValidationResult<List<String>> validateUnique(List<String> values) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        HashSet<String> unique = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            ValidationResult<String> syntax = validate(value);
            syntax.errors().forEach(errors::add);
            if (!unique.add(value)) {
                errors.add(new ValidationError(
                        DataValidationErrorCode.DUPLICATE_CONTRACT_ID,
                        "contractIds[" + index + "]", "duplicate contract ID"));
            }
        }
        return errors.isEmpty() ? ValidationResult.valid(List.copyOf(values)) : ValidationResult.invalid(errors);
    }
}
