package com.jc.backend.database;

import org.springframework.transaction.TransactionDefinition;

/** Transaction propagation modes permitted by the database role boundary. */
public enum DatabasePropagation {
    REQUIRED(TransactionDefinition.PROPAGATION_REQUIRED),
    REQUIRES_NEW(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    private final int value;

    DatabasePropagation(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
