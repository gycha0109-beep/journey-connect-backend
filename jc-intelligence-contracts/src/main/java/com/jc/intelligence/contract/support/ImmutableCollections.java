package com.jc.intelligence.contract.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ImmutableCollections {
    private ImmutableCollections() {
    }

    public static <T> List<T> orderedCopy(List<T> source, String name) {
        Objects.requireNonNull(source, name);
        ArrayList<T> copy = new ArrayList<>(source.size());
        for (T value : source) {
            copy.add(Objects.requireNonNull(value, name + " item"));
        }
        return Collections.unmodifiableList(copy);
    }

    public static <K, V> Map<K, V> insertionOrderedCopy(Map<K, V> source, String name) {
        Objects.requireNonNull(source, name);
        LinkedHashMap<K, V> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, name + " key"),
                Objects.requireNonNull(value, name + " value")));
        return Collections.unmodifiableMap(copy);
    }
}
