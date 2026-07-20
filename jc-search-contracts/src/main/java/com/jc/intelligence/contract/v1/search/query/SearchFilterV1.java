package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchFilterSource;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public record SearchFilterV1(
        SearchFilterType filterType,
        List<String> values,
        SearchFilterSource source,
        SchemaVersion schemaVersion) {
    public SearchFilterV1 {
        SearchChecks.requireNonNull(filterType, "filterType");
        SearchChecks.requireNonNull(source, "filterSource");
        SearchChecks.requireNonNull(schemaVersion, "filterSchemaVersion");
        if (!"search-filter-v1".equals(schemaVersion.value())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_VERSION_INVALID,
                    "SearchFilterV1 requires search-filter-v1 schema");
        }
        if (values == null || values.isEmpty()) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_FILTER_INVALID, "filter values are required");
        }
        TreeSet<String> canonical = new TreeSet<>();
        for (String value : values) canonical.add(canonicalValue(value));
        if (!filterType.multiValued() && canonical.size() != 1) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_FILTER_INVALID, "single-value filter has conflicting values");
        }
        values = List.copyOf(new ArrayList<>(canonical));
    }

    private static String canonicalValue(String value) {
        SearchChecks.requireText(value, "filterValue");
        com.jc.intelligence.contract.v1.search.validation.SearchQueryValidatorV1.validateScalarText(value);
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replaceAll("\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 256) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_FILTER_INVALID, "invalid filter value");
        }
        return normalized;
    }

    private static String normalizeWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean pending = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pending = result.length() > 0;
            } else {
                if (pending) result.append(' ');
                pending = false;
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString().trim();
    }
}
