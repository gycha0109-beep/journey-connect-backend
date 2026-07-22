package com.jc.data.contract.v1.retry;

import com.jc.data.contract.support.Sha256DigestV1;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class FailureSignatureV1 {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern COMPONENT = Pattern.compile("[a-z][a-z0-9]*(?:[-_:][a-z0-9]+){0,15}");

    private FailureSignatureV1() {
    }

    public static String normalized(
            RetryFailureClassV1 failureClass,
            String failureCode,
            String stableComponentRef) {
        Objects.requireNonNull(failureClass, "failureClass");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(stableComponentRef, "stableComponentRef");
        String normalizedCode = failureCode.trim().toUpperCase(Locale.ROOT);
        String normalizedComponent = stableComponentRef.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("failureCode must be stable UPPER_SNAKE_CASE");
        }
        if (!COMPONENT.matcher(normalizedComponent).matches()) {
            throw new IllegalArgumentException("stableComponentRef is malformed");
        }
        String canonical = "data-failure-signature-v1\u001f"
                + failureClass.wireValue() + "\u001f" + normalizedCode + "\u001f" + normalizedComponent;
        return Sha256DigestV1.lowercaseHex(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
