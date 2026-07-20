package com.jc.backend.database;

/** PostgreSQL runtime roles that may be selected by the backend transaction boundary. */
public enum DatabaseRole {
    APP("jc_app"),
    AUTH("jc_auth"),
    RECOMMENDATION("jc_recommendation");

    private final String sqlName;

    DatabaseRole(String sqlName) {
        this.sqlName = sqlName;
    }

    public String sqlName() {
        return sqlName;
    }
}
