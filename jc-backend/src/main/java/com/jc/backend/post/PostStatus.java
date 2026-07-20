package com.jc.backend.post;

public enum PostStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    DELETED("deleted");

    private final String databaseValue;

    PostStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PostStatus fromDatabase(String value) {
        for (PostStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown post status: " + value);
    }
}
