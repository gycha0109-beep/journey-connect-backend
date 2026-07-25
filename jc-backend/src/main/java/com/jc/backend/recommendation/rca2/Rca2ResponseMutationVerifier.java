package com.jc.backend.recommendation.rca2;

import java.util.Map;
import java.util.Objects;

public final class Rca2ResponseMutationVerifier {
    public record ResponseSnapshot(
            byte[] serializedBytes,
            int httpStatus,
            Map<String, String> headers,
            String bodyDigest,
            String orderingDigest,
            int itemCount,
            String cursorDigest,
            String p1SourceReference,
            String p2SourceReference) {
        public ResponseSnapshot {
            serializedBytes = serializedBytes.clone();
            headers = Map.copyOf(headers);
        }
        @Override public byte[] serializedBytes() { return serializedBytes.clone(); }
    }

    public void verifyUnchanged(ResponseSnapshot disabled, ResponseSnapshot enabled) {
        Objects.requireNonNull(disabled, "disabled");
        Objects.requireNonNull(enabled, "enabled");
        if (!java.util.Arrays.equals(disabled.serializedBytes(), enabled.serializedBytes())
                || disabled.httpStatus() != enabled.httpStatus()
                || !disabled.headers().equals(enabled.headers())
                || !disabled.bodyDigest().equals(enabled.bodyDigest())
                || !disabled.orderingDigest().equals(enabled.orderingDigest())
                || disabled.itemCount() != enabled.itemCount()
                || !disabled.cursorDigest().equals(enabled.cursorDigest())
                || !disabled.p1SourceReference().equals(enabled.p1SourceReference())
                || !disabled.p2SourceReference().equals(enabled.p2SourceReference())) {
            throw new IllegalStateException("SHADOW_RESPONSE_MUTATION_DETECTED");
        }
    }
}
