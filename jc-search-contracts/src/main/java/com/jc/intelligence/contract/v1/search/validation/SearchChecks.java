package com.jc.intelligence.contract.v1.search.validation;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SearchChecks {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SIMPLE_REF = Pattern.compile("[a-z][a-z0-9_\\-]*:[^\\s:][^\\s]{0,254}");

    private SearchChecks() { }

    public static SearchContractValidationException invalid(SearchValidationErrorCode code, String message) {
        return new SearchContractValidationException(code, message);
    }

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) throw invalid(SearchValidationErrorCode.SEARCH_NULL_INVALID, field + " is required");
        return value;
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid(SearchValidationErrorCode.SEARCH_REFERENCE_INVALID, field + " must be nonblank and trimmed");
        }
        return value;
    }


    public static String requireOpaqueId(String value, String field) {
        if (value == null) return null;
        requireText(value, field);
        if (value.length() > 256 || value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(SearchValidationErrorCode.SEARCH_REFERENCE_INVALID,
                    field + " contains whitespace or exceeds 256 characters");
        }
        return value;
    }

    public static String requireOptionalRef(String value, String field) {
        if (value == null) return null;
        requireText(value, field);
        if (value.length() > 256 || !SIMPLE_REF.matcher(value).matches()) {
            throw invalid(SearchValidationErrorCode.SEARCH_REFERENCE_INVALID, field + " must be a stable namespaced reference");
        }
        return value;
    }

    public static String requireFingerprint(String value, String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw invalid(SearchValidationErrorCode.SEARCH_FINGERPRINT_INVALID, field + " must be lowercase SHA-256 hex");
        }
        return value;
    }

    public static int requirePositive(int value, String field) {
        if (value < 1) throw invalid(SearchValidationErrorCode.SEARCH_RANK_INVALID, field + " must be positive and 1-based");
        return value;
    }

    public static int requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) throw invalid(SearchValidationErrorCode.SEARCH_REQUEST_INVALID, field + " outside allowed range");
        return value;
    }

    public static Double requireFinite(Double value, String field) {
        if (value != null && !Double.isFinite(value.doubleValue())) {
            throw invalid(SearchValidationErrorCode.SEARCH_SCORE_INVALID, field + " must be finite when present");
        }
        return value;
    }

    public static Instant requireInstant(Instant value, String field) {
        return requireNonNull(value, field);
    }

    public static void requireOrdered(Instant start, Instant end, String startField, String endField) {
        requireInstant(start, startField);
        requireInstant(end, endField);
        if (end.isBefore(start)) throw invalid(SearchValidationErrorCode.SEARCH_REQUEST_INVALID, endField + " cannot precede " + startField);
    }

    public static String requireNoWhitespace(String value, String field, int maxLength) {
        requireText(value, field);
        if (value.length() > maxLength || value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(SearchValidationErrorCode.SEARCH_REFERENCE_INVALID, field + " contains whitespace or exceeds " + maxLength);
        }
        return value;
    }

    public static <T> T same(T actual, T expected, SearchValidationErrorCode code, String message) {
        if (!Objects.equals(actual, expected)) throw invalid(code, message);
        return actual;
    }
}
