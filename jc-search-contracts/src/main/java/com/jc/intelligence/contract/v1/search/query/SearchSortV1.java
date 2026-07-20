package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.PolicyVersion;

public record SearchSortV1(SearchSortType sortType, PolicyVersion sortPolicyVersion) {
    public SearchSortV1 {
        SearchChecks.requireNonNull(sortType, "sortType");
        SearchVersionValidatorV1.requirePolicy(sortPolicyVersion, "sortPolicyVersion");
    }
}
