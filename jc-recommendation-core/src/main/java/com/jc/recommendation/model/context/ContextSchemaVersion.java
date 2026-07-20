package com.jc.recommendation.model.context;

public enum ContextSchemaVersion {
    V1("context-v1");

    private final String wireValue;

    ContextSchemaVersion(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
