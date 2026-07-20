package com.jc.recommendation.p1.profile;

public enum UserProfileSegment {
    EMPTY("empty"),
    EXPLICIT_ONLY("explicit_only"),
    EMERGING("emerging"),
    ESTABLISHED("established");

    private final String wireValue;

    UserProfileSegment(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
