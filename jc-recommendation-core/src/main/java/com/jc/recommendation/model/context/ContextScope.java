package com.jc.recommendation.model.context;

public enum ContextScope {
    REQUEST("request"), SESSION("session");
    private final String wireValue;
    ContextScope(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
