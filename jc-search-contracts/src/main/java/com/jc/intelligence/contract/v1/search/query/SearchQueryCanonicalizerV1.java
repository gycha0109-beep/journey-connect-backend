package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.validation.SearchQueryValidatorV1;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

public final class SearchQueryCanonicalizerV1 {
    private static final SchemaVersion VERSION = new SchemaVersion(SearchContractIds.SEARCH_QUERY_NORMALIZATION.value());
    private static final String DOMAIN = "journey-connect:search-query-fingerprint:v1\n";

    private SearchQueryCanonicalizerV1() { }

    public static SearchQueryV1 canonicalize(
            SearchQueryMode mode,
            String originalQuery,
            String languageHint,
            String localeHint) {
        SearchQueryValidatorV1.validateOriginal(originalQuery, mode);
        String normalized = originalQuery == null ? null : normalize(originalQuery);
        if (mode == SearchQueryMode.BROWSE && normalized != null && normalized.isEmpty()) normalized = null;
        SearchQueryValidatorV1.validateNormalized(normalized, mode);
        String fingerprint = fingerprint(mode, normalized, VERSION);
        int codePoints = normalized == null ? 0 : normalized.codePointCount(0, normalized.length());
        int bytes = normalized == null ? 0 : normalized.getBytes(StandardCharsets.UTF_8).length;
        return new SearchQueryV1(mode, originalQuery, normalized, fingerprint, VERSION,
                languageHint, localeHint, codePoints, bytes);
    }

    public static String normalize(String value) {
        SearchQueryValidatorV1.validateOriginal(value, SearchQueryMode.TEXT_QUERY);
        StringBuilder spaced = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = spaced.length() > 0;
            } else {
                if (pendingSpace) spaced.append(' ');
                pendingSpace = false;
                spaced.appendCodePoint(cp);
            }
        }
        String normalized = Normalizer.normalize(spaced.toString().trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        normalized = collapseWhitespace(normalized);
        SearchQueryValidatorV1.validateNormalized(normalized, SearchQueryMode.TEXT_QUERY);
        return normalized;
    }

    public static String fingerprint(SearchQueryMode mode, String normalizedQuery, SchemaVersion version) {
        String material = DOMAIN + version.value() + "\n" + mode.wireValue() + "\n"
                + (normalizedQuery == null ? "" : normalizedQuery);
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    private static String collapseWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean pending = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pending = result.length() > 0;
            } else {
                if (pending) result.append(' ');
                pending = false;
                result.appendCodePoint(cp);
            }
        }
        return result.toString().trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
