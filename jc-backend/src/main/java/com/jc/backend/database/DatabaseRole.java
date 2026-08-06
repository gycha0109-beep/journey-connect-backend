package com.jc.backend.database;

/** PostgreSQL runtime roles that may be selected by the backend transaction boundary. */
public enum DatabaseRole {
    APP("jc_app", true),
    AUTH("jc_auth", true),
    ADMIN("jc_admin", true),
    RECOMMENDATION("jc_recommendation", true),
    RELIABILITY("jc_reliability", false);

    private final String sqlName;
    private final boolean requiredAtStartup;

    DatabaseRole(String sqlName, boolean requiredAtStartup) {
        this.sqlName = sqlName;
        this.requiredAtStartup = requiredAtStartup;
    }

    public String sqlName() {
        return sqlName;
    }

    public boolean requiredAtStartup() {
        return requiredAtStartup;
    }
}
