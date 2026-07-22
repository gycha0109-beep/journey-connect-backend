package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RankedReference(String reference, long count, Instant lastOccurredAt) {
    public RankedReference {
        reference = ProjectionEngineSupport.requireReference(reference, "reference");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        Objects.requireNonNull(lastOccurredAt, "lastOccurredAt");
    }

    public Map<String, Object> canonicalFields() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("reference", reference);
        fields.put("count", count);
        fields.put("lastOccurredAt", lastOccurredAt);
        return Map.copyOf(fields);
    }
}
