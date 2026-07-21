package com.jc.data.contract.v1.identity;

import java.util.Arrays;
import java.util.Optional;

public enum IdentityScheme {
    PLATFORM_SUBJECT_V1("platform_subject_v1", "subject", false),
    LEGACY_USER_NUMERIC_V1("legacy_user_numeric_v1", "user", false);

    private final String wireValue;
    private final String prefix;
    private final boolean automaticConversionAllowed;

    IdentityScheme(String wireValue, String prefix, boolean automaticConversionAllowed) {
        this.wireValue = wireValue;
        this.prefix = prefix;
        this.automaticConversionAllowed = automaticConversionAllowed;
    }

    public String wireValue() {
        return wireValue;
    }

    public String prefix() {
        return prefix;
    }

    public boolean automaticConversionAllowed() {
        return automaticConversionAllowed;
    }

    public static Optional<IdentityScheme> fromWire(String wireValue) {
        return Arrays.stream(values()).filter(value -> value.wireValue.equals(wireValue)).findFirst();
    }
}
