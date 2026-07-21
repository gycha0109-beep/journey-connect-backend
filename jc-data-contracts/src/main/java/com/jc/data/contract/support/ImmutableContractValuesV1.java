package com.jc.data.contract.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ImmutableContractValuesV1 {
    private ImmutableContractValuesV1() {
    }

    public static Map<String, Object> copyMap(Map<String, ?> source) {
        Objects.requireNonNull(source, "source");
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "map key");
            if (copy.containsKey(key)) {
                throw new IllegalArgumentException("duplicate map key: " + key);
            }
            copy.put(key, copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public static List<Object> copyList(List<?> source) {
        Objects.requireNonNull(source, "source");
        ArrayList<Object> copy = new ArrayList<>(source.size());
        for (Object value : source) {
            copy.add(copyValue(value));
        }
        return Collections.unmodifiableList(copy);
    }

    public static Object copyValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof Instant) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("contract map keys must be strings");
                }
                if (converted.containsKey(key)) {
                    throw new IllegalArgumentException("duplicate map key: " + key);
                }
                converted.put(key, copyValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(converted);
        }
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        throw new IllegalArgumentException("unsupported contract value type: " + value.getClass().getName());
    }
}
