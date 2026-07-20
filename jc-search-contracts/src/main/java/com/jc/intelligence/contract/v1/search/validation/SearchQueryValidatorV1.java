package com.jc.intelligence.contract.v1.search.validation;

import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import java.nio.charset.StandardCharsets;

public final class SearchQueryValidatorV1 {
    public static final int MAX_CODE_POINTS = 256;
    public static final int MAX_UTF8_BYTES = 1024;

    private SearchQueryValidatorV1() { }

    public static void validateOriginal(String value, SearchQueryMode mode) {
        SearchChecks.requireNonNull(mode, "queryMode");
        if (value == null) {
            if (mode == SearchQueryMode.TEXT_QUERY) throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_BLANK, "text_query requires query text");
            return;
        }
        validateScalarText(value);
        if (value.isBlank()) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_BLANK,
                    mode == SearchQueryMode.TEXT_QUERY ? "text_query cannot be blank" : "browse query must be absent, not blank");
        }
        validateLength(value);
    }

    public static void validateNormalized(String value, SearchQueryMode mode) {
        if (mode == SearchQueryMode.TEXT_QUERY && (value == null || value.isBlank())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_BLANK, "normalized text query cannot be blank");
        }
        if (value == null) return;
        if (!value.equals(value.trim()) || containsRepeatedAsciiSpace(value)) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_UNSUPPORTED_CHARACTER, "normalized query must be trimmed with single spaces");
        }
        validateScalarText(value);
        validateLength(value);
    }

    public static void validateScalarText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isHighSurrogate(ch)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw unsupported("unpaired high surrogate");
                }
                index++;
                continue;
            }
            if (Character.isLowSurrogate(ch)) throw unsupported("unpaired low surrogate");
            int codePoint = ch;
            if (codePoint == 0 || isBidiOverride(codePoint) || isNonCharacter(codePoint)) throw unsupported("forbidden code point");
            if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) throw unsupported("non-whitespace control character");
        }
        value.codePoints().forEach(codePoint -> {
            if (isBidiOverride(codePoint) || isNonCharacter(codePoint)) throw unsupported("forbidden code point");
        });
    }

    public static void validateLength(String value) {
        if (value.codePointCount(0, value.length()) > MAX_CODE_POINTS
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_TOO_LONG, "query exceeds V1 size limits");
        }
    }

    private static boolean containsRepeatedAsciiSpace(String value) { return value.contains("  "); }
    private static boolean isBidiOverride(int cp) { return (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069); }
    private static boolean isNonCharacter(int cp) { return (cp >= 0xFDD0 && cp <= 0xFDEF) || (cp & 0xFFFF) == 0xFFFE || (cp & 0xFFFF) == 0xFFFF; }
    private static SearchContractValidationException unsupported(String message) {
        return SearchChecks.invalid(SearchValidationErrorCode.SEARCH_QUERY_UNSUPPORTED_CHARACTER, message);
    }
}
