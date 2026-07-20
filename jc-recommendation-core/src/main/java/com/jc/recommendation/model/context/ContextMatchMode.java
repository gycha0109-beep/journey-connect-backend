package com.jc.recommendation.model.context;

public enum ContextMatchMode {
    ANY("any"), ALL("all");
    private final String wireValue;
    ContextMatchMode(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
