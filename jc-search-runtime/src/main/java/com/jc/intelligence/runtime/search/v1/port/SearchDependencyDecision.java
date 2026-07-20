package com.jc.intelligence.runtime.search.v1.port;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchDependencyDecision implements WireValue {
    ALLOW("allow"), DENY("deny"), UNKNOWN("unknown"), DEPENDENCY_UNAVAILABLE("dependency_unavailable");
    private final String wireValue;
    SearchDependencyDecision(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
