package com.jc.recommendation.model.diversity;

public enum DiversityRerankStatus {
    UNCHANGED("unchanged"), RERANKED("reranked");
    private final String wireValue;
    DiversityRerankStatus(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
