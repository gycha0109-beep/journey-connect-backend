package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchQueryValidatorV1;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.SchemaVersion;

public record SearchQueryV1(
        SearchQueryMode queryMode,
        String originalQuery,
        String normalizedQuery,
        String queryFingerprint,
        SchemaVersion normalizationVersion,
        String languageHint,
        String localeHint,
        int codePointLength,
        int utf8SizeBytes) {
    public SearchQueryV1 {
        SearchChecks.requireNonNull(queryMode, "queryMode");
        SearchVersionValidatorV1.requireQueryNormalization(normalizationVersion);
        SearchQueryValidatorV1.validateOriginal(originalQuery, queryMode);
        SearchQueryValidatorV1.validateNormalized(normalizedQuery, queryMode);
        String expectedNormalized = originalQuery == null ? null : SearchQueryCanonicalizerV1.normalize(originalQuery);
        if (!java.util.Objects.equals(normalizedQuery, expectedNormalized)) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "normalizedQuery must match the V1 canonicalizer output");
        }
        SearchChecks.requireFingerprint(queryFingerprint, "queryFingerprint");
        String expectedFingerprint = SearchQueryCanonicalizerV1.fingerprint(queryMode, normalizedQuery, normalizationVersion);
        if (!queryFingerprint.equals(expectedFingerprint)) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_FINGERPRINT_INVALID,
                    "queryFingerprint must match normalized query and version");
        }
        if (languageHint != null) languageHint = SearchChecks.requireNoWhitespace(languageHint, "languageHint", 32);
        if (localeHint != null) localeHint = SearchChecks.requireNoWhitespace(localeHint, "localeHint", 64);
        int expectedCodePoints = normalizedQuery == null ? 0 : normalizedQuery.codePointCount(0, normalizedQuery.length());
        int expectedBytes = normalizedQuery == null ? 0 : normalizedQuery.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (codePointLength != expectedCodePoints || utf8SizeBytes != expectedBytes) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "query length metadata must match normalized query");
        }
    }
}
