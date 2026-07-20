package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum ProjectionPublicationStatus implements WireValue {
    PUBLISHED("published"), DRAFT("draft"), DELETED("deleted"), UNKNOWN("unknown");
    private final String wireValue;
    ProjectionPublicationStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
