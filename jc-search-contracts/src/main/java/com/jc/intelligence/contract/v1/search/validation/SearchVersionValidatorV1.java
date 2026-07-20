package com.jc.intelligence.contract.v1.search.validation;

import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;

public final class SearchVersionValidatorV1 {
    private SearchVersionValidatorV1() { }
    public static void requireContract(ContractId actual, ContractId expected) {
        if (!expected.equals(actual)) throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_VERSION_INVALID, "unexpected search contract version");
    }
    public static void requireQueryNormalization(SchemaVersion version) {
        SearchChecks.requireNonNull(version, "queryNormalizationVersion");
        if (!SearchContractIds.SEARCH_QUERY_NORMALIZATION.value().equals(version.value())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_VERSION_INVALID, "unsupported query normalization version");
        }
    }
    public static void requireCursorVersion(SchemaVersion version) {
        SearchChecks.requireNonNull(version, "cursorVersion");
        if (!SearchContractIds.SEARCH_PAGINATION_CURSOR.value().equals(version.value())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID, "unknown cursor version");
        }
    }
    public static void requirePolicy(PolicyVersion version, String field) {
        if (version == null) throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_POLICY_VERSION_INVALID, field + " is required");
    }
}
