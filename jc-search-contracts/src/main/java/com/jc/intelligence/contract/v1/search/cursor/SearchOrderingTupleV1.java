package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import java.util.ArrayList;
import java.util.List;

public record SearchOrderingTupleV1(List<String> components) {
    public SearchOrderingTupleV1 {
        if (components == null || components.isEmpty() || components.size() > 16) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID,
                    "ordering tuple requires 1..16 components");
        }
        List<String> copy = new ArrayList<>(components.size());
        for (String component : components) {
            String value = SearchChecks.requireText(component, "orderingTuple component");
            if (value.length() > 256) {
                throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID,
                        "ordering tuple component exceeds 256 characters");
            }
            copy.add(value);
        }
        components = List.copyOf(copy);
    }
}
