package com.jc.data.contract.v1.fingerprint;

import com.jc.data.contract.v1.validation.ValidationError;
import java.util.List;

public record FingerprintResultV1(
        FingerprintStatus status,
        String fingerprintValue,
        List<ValidationError> errors) {
    public FingerprintResultV1 {
        errors = List.copyOf(errors);
        if (status == FingerprintStatus.SUCCESS && (fingerprintValue == null || !errors.isEmpty())) {
            throw new IllegalArgumentException("successful fingerprint result requires value without errors");
        }
        if (status != FingerprintStatus.SUCCESS && fingerprintValue != null) {
            throw new IllegalArgumentException("blocked fingerprint result cannot expose value");
        }
    }
}
