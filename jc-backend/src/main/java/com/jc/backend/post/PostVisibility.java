package com.jc.backend.post;

public enum PostVisibility {
    PUBLIC("public"),
    FOLLOWERS("followers"),
    PRIVATE("private");

    private final String databaseValue;

    PostVisibility(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PostVisibility fromDatabase(String value) {
        for (PostVisibility visibility : values()) {
            if (visibility.databaseValue.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown post visibility: " + value);
    }
}
