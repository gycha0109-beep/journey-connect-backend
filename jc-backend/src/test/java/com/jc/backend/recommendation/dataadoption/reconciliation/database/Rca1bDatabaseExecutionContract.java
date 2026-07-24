package com.jc.backend.recommendation.dataadoption.reconciliation.database;

final class Rca1bDatabaseExecutionContract {
    static final String SERVER_ASSERTIONS = String.join(";",
            "SHOW transaction_read_only",
            "SHOW transaction_isolation",
            "SHOW statement_timeout",
            "SHOW lock_timeout",
            "SHOW idle_in_transaction_session_timeout",
            "SHOW TimeZone");

    static final int STATEMENT_TIMEOUT_MS = 5_000;
    static final int LOCK_TIMEOUT_MS = 1_000;
    static final int IDLE_IN_TRANSACTION_TIMEOUT_MS = 5_000;
    static final int MAX_RESULT_ROWS_PER_QUERY = 1_000;
    static final int MAX_RECONCILIATION_CASES = 10_000;
    static final int MAX_EXECUTION_DURATION_SECONDS = 900;
    static final int MAX_RECONCILIATION_CONNECTIONS = 2;
    static final int CURSOR_FETCH_SIZE = 100;

    private Rca1bDatabaseExecutionContract() {}
}
