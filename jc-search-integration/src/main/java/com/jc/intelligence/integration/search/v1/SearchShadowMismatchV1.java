package com.jc.intelligence.integration.search.v1;

public record SearchShadowMismatchV1(
        SearchShadowMismatchCode code,
        SearchShadowSeverity severity,
        String entityRef,
        Integer legacyPosition,
        Integer runtimePosition) implements Comparable<SearchShadowMismatchV1> {
    public SearchShadowMismatchV1 {
        if (code == null || severity == null) throw new IllegalArgumentException("code and severity are required");
        if (entityRef != null && (entityRef.isBlank() || !entityRef.equals(entityRef.trim()))) {
            throw new IllegalArgumentException("entityRef must be trimmed when present");
        }
        if (legacyPosition != null && legacyPosition.intValue() < 1) throw new IllegalArgumentException("legacyPosition is 1-based");
        if (runtimePosition != null && runtimePosition.intValue() < 1) throw new IllegalArgumentException("runtimePosition is 1-based");
    }

    @Override public int compareTo(SearchShadowMismatchV1 other) {
        int byCode = Integer.compare(code.ordinal(), other.code.ordinal());
        if (byCode != 0) return byCode;
        int byEntity = nullSafe(entityRef).compareTo(nullSafe(other.entityRef));
        if (byEntity != 0) return byEntity;
        int byLegacy = Integer.compare(value(legacyPosition), value(other.legacyPosition));
        if (byLegacy != 0) return byLegacy;
        return Integer.compare(value(runtimePosition), value(other.runtimePosition));
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }
    private static int value(Integer value) { return value == null ? 0 : value.intValue(); }
}
