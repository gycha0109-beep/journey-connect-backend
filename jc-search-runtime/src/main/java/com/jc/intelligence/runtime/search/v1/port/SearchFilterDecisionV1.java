package com.jc.intelligence.runtime.search.v1.port;

import java.util.Objects;

public record SearchFilterDecisionV1(boolean included, String reasonCode) {
    public SearchFilterDecisionV1 {
        if (!included) Objects.requireNonNull(reasonCode, "reasonCode");
    }
    public static SearchFilterDecisionV1 include() { return new SearchFilterDecisionV1(true, null); }
    public static SearchFilterDecisionV1 exclude(String reasonCode) { return new SearchFilterDecisionV1(false, reasonCode); }
}
