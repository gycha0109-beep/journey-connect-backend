package com.jc.intelligence.compat.search.explore.v1;

public record LegacyExplorePageMetadata(
        int page, int size, long offset, long totalElements, int totalPages, boolean last, boolean cursorAvailable) {
    public LegacyExplorePageMetadata {
        if (page < 0 || size < 1 || offset < 0 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("legacy page metadata is out of range");
        }
        if (offset != Math.multiplyExact((long) page, size)) {
            throw new IllegalArgumentException("legacy page offset must equal page * size");
        }
        if (cursorAvailable) throw new IllegalArgumentException("legacy explore compatibility must not expose SearchCursor");
    }
}
