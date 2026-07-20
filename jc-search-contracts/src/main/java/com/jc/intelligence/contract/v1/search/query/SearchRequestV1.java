package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorValidatorV1;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.util.List;

public record SearchRequestV1(
        ContractId contractVersion,
        String requestId,
        String correlationId,
        SearchQueryV1 query,
        SearchContextV1 context,
        List<SearchFilterV1> filters,
        SearchSortV1 sort,
        SearchPageRequestV1 pageRequest,
        SchemaVersion schemaVersion,
        SchemaVersion queryNormalizationVersion,
        PolicyVersion rankingPolicyVersion,
        FeatureDefinitionVersion featureDefinitionVersion) {
    public SearchRequestV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_DOMAIN);
        if (requestId != null) requestId = SearchChecks.requireOpaqueId(requestId, "requestId");
        if (correlationId != null) correlationId = SearchChecks.requireOpaqueId(correlationId, "correlationId");
        SearchChecks.requireNonNull(query, "query");
        SearchChecks.requireNonNull(context, "context");
        filters = SearchFilterCanonicalizerV1.canonicalize(filters);
        SearchChecks.requireNonNull(sort, "sort");
        SearchChecks.requireNonNull(pageRequest, "pageRequest");
        SearchChecks.requireNonNull(schemaVersion, "schemaVersion");
        if (!"search-request-v1".equals(schemaVersion.value())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_VERSION_INVALID,
                    "SearchRequestV1 requires search-request-v1 schema");
        }
        SearchVersionValidatorV1.requireQueryNormalization(queryNormalizationVersion);
        SearchVersionValidatorV1.requirePolicy(rankingPolicyVersion, "rankingPolicyVersion");
        SearchChecks.requireNonNull(featureDefinitionVersion, "featureDefinitionVersion");
        if (!query.normalizationVersion().equals(queryNormalizationVersion)) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_VERSION_INVALID,
                    "request and query normalization versions must match");
        }
        SearchSortValidatorV1.validate(sort, query, context.entityScope());
        validateEntityScopeFilter(filters, context.entityScope());
        if (pageRequest.cursor() != null) {
            SearchCursorValidatorV1.validateBinding(
                    pageRequest.cursor(),
                    query.queryFingerprint(),
                    SearchFilterCanonicalizerV1.fingerprint(filters),
                    rankingPolicyVersion,
                    context.surface(),
                    context.entityScope(),
                    context.subjectRef(),
                    context.sessionRef(),
                    context.referenceTime());
        }
    }

    private static void validateEntityScopeFilter(
            List<SearchFilterV1> filters,
            com.jc.intelligence.contract.v1.search.SearchEntityScope scope) {
        for (SearchFilterV1 filter : filters) {
            if (filter.filterType() == com.jc.intelligence.contract.v1.search.SearchFilterType.ENTITY_SCOPE
                    && !filter.values().get(0).equals(scope.wireValue())) {
                throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_FILTER_INVALID,
                        "entity_scope filter must match SearchContext entityScope");
            }
        }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private ContractId contractVersion;
        private String requestId;
        private String correlationId;
        private SearchQueryV1 query;
        private SearchContextV1 context;
        private List<SearchFilterV1> filters = List.of();
        private SearchSortV1 sort;
        private SearchPageRequestV1 pageRequest;
        private SchemaVersion schemaVersion;
        private SchemaVersion queryNormalizationVersion;
        private PolicyVersion rankingPolicyVersion;
        private FeatureDefinitionVersion featureDefinitionVersion;

        public Builder contractVersion(ContractId value) { contractVersion = value; return this; }
        public Builder requestId(String value) { requestId = value; return this; }
        public Builder correlationId(String value) { correlationId = value; return this; }
        public Builder query(SearchQueryV1 value) { query = value; return this; }
        public Builder context(SearchContextV1 value) { context = value; return this; }
        public Builder filters(List<SearchFilterV1> value) { filters = value; return this; }
        public Builder sort(SearchSortV1 value) { sort = value; return this; }
        public Builder pageRequest(SearchPageRequestV1 value) { pageRequest = value; return this; }
        public Builder schemaVersion(SchemaVersion value) { schemaVersion = value; return this; }
        public Builder queryNormalizationVersion(SchemaVersion value) { queryNormalizationVersion = value; return this; }
        public Builder rankingPolicyVersion(PolicyVersion value) { rankingPolicyVersion = value; return this; }
        public Builder featureDefinitionVersion(FeatureDefinitionVersion value) { featureDefinitionVersion = value; return this; }

        public SearchRequestV1 build() {
            return new SearchRequestV1(contractVersion, requestId, correlationId, query, context, filters,
                    sort, pageRequest, schemaVersion, queryNormalizationVersion,
                    rankingPolicyVersion, featureDefinitionVersion);
        }
    }
}
