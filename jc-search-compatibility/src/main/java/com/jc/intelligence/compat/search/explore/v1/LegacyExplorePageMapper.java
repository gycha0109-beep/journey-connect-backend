package com.jc.intelligence.compat.search.explore.v1;

public final class LegacyExplorePageMapper {
    public LegacyExplorePageMetadata map(LegacyExplorePageView page) {
        if (page == null || page.items() == null || page.page() == null || page.size() == null
                || page.totalElements() == null || page.totalPages() == null || page.last() == null) {
            throw failure(LegacyExploreMappingFailureCode.INVALID_PAGE_METADATA, "page", "page metadata is incomplete");
        }
        int number = page.page(); int size = page.size(); long total = page.totalElements(); int totalPages = page.totalPages();
        if (number < 0 || size < 1 || size > LegacyExploreCompatibilityPolicy.MAX_PAGE_SIZE
                || total < 0 || totalPages < 0 || page.items().size() > size || total < page.items().size()) {
            throw failure(LegacyExploreMappingFailureCode.INVALID_PAGE_METADATA, "page", "page metadata is out of range");
        }
        long expectedPageCount = total == 0 ? 0 : ((total - 1L) / size) + 1L;
        if (expectedPageCount > Integer.MAX_VALUE) {
            throw failure(LegacyExploreMappingFailureCode.INVALID_PAGE_METADATA, "totalPages", "total page count exceeds integer range");
        }
        int expectedPages = (int) expectedPageCount;
        if (totalPages != expectedPages || (!page.last() && number + 1 >= totalPages)
                || (page.last() && totalPages > 0 && number + 1 < totalPages)) {
            throw failure(LegacyExploreMappingFailureCode.LEGACY_PAYLOAD_INCONSISTENCY, "page",
                    "page totals/last flag are inconsistent");
        }
        final long offset;
        try {
            offset = Math.multiplyExact((long) number, size);
        } catch (ArithmeticException exception) {
            throw failure(LegacyExploreMappingFailureCode.INVALID_PAGE_METADATA, "page", "offset exceeds long range");
        }
        return new LegacyExplorePageMetadata(number, size, offset, total, totalPages, page.last(), false);
    }

    private static LegacyExploreMappingException failure(
            LegacyExploreMappingFailureCode code, String field, String message) {
        return new LegacyExploreMappingException(LegacyExploreCompatibilityStatus.MAPPING_FAILURE,
                new LegacyExploreMappingFailure(code, field, null, message));
    }
}
