package com.jc.recommendation.model.event;

public enum EventSurface {
    HOME("home"),
    SEARCH("search"),
    DETAIL("detail"),
    PROFILE("profile"),
    CREW("crew");

    private final String wireValue;

    EventSurface(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
