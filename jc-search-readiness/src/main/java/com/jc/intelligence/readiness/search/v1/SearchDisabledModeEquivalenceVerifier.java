package com.jc.intelligence.readiness.search.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SearchDisabledModeEquivalenceVerifier {
    public <T> SearchDisabledModeEquivalenceResultV1 verify(SearchDisabledModeObservationV1<T> observation) {
        Objects.requireNonNull(observation, "observation");
        List<String> violations = new ArrayList<>();
        if (observation.responseBefore() != observation.responseAfter()) violations.add("response_identity_changed");
        if (!observation.responseBefore().equals(observation.responseAfter())) violations.add("response_value_changed");
        if (observation.executorSubmissions() != 0) violations.add("executor_submitted");
        if (observation.runtimeInvocations() != 0) violations.add("runtime_invoked");
        if (observation.comparisonInvocations() != 0) violations.add("comparison_invoked");
        if (observation.loggingInvocations() != 0) violations.add("logging_invoked");
        if (!observation.itemOrderPreserved()) violations.add("item_order_changed");
        if (!observation.paginationPreserved()) violations.add("pagination_changed");
        if (!observation.exceptionSemanticsPreserved()) violations.add("exception_semantics_changed");
        if (!observation.serializationPreserved()) violations.add("serialization_changed");
        violations.sort(String::compareTo);
        return new SearchDisabledModeEquivalenceResultV1(violations.isEmpty(), violations);
    }
}
