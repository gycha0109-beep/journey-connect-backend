package com.jc.recommendation.model.context;

public enum ContextSurface {
    HOME_FEED("home_feed"),
    SEARCH_RESULT("search_result"),
    JOURNEY_RECOMMENDATION("journey_recommendation"),
    PLACE_RECOMMENDATION("place_recommendation"),
    CREW_RECOMMENDATION("crew_recommendation");

    private final String wireValue;
    ContextSurface(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
