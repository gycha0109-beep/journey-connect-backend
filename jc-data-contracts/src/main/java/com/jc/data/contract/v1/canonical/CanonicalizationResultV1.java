package com.jc.data.contract.v1.canonical;

import com.jc.data.contract.v1.validation.ValidationError;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record CanonicalizationResultV1(byte[] canonicalBytes, List<ValidationError> errors) {
    public CanonicalizationResultV1 {
        canonicalBytes = canonicalBytes == null ? null : canonicalBytes.clone();
        errors = List.copyOf(errors);
        if (errors.isEmpty() == (canonicalBytes == null)) {
            throw new IllegalArgumentException("canonicalization result state mismatch");
        }
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes == null ? null : canonicalBytes.clone();
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    public String utf8() {
        return canonicalBytes == null ? null : new String(canonicalBytes, StandardCharsets.UTF_8);
    }
}
