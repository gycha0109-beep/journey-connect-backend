package com.jc.recommendation.model.context;

public enum ContextClauseSource {
    EXPLICIT("explicit"),
    VALIDATED_QUERY("validated_query"),
    SYSTEM("system"),
    AI("ai");

    private final String wireValue;

    ContextClauseSource(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
