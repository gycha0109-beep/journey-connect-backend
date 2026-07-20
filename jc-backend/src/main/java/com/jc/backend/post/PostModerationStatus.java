package com.jc.backend.post;

public enum PostModerationStatus {
    VISIBLE("visible"),
    HIDDEN("hidden");

    private final String databaseValue;

    PostModerationStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PostModerationStatus fromDatabase(String value) {
        for (PostModerationStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown moderation status: " + value);
    }
}
