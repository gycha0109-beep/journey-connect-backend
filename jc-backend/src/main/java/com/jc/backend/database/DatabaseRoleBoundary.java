package com.jc.backend.database;

import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Applies one immutable PostgreSQL role and one verified request identity per transaction. */
@Component
public final class DatabaseRoleBoundary {

    private static final Object ROLE_RESOURCE_KEY = DatabaseRoleBoundary.class.getName() + ".ROLE";
    private static final Object USER_RESOURCE_KEY = DatabaseRoleBoundary.class.getName() + ".USER";
    private static final Object ANONYMOUS_USER = new Object();

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseRequestIdentity requestIdentity;

    public DatabaseRoleBoundary(JdbcTemplate jdbcTemplate, DatabaseRequestIdentity requestIdentity) {
        this.jdbcTemplate = jdbcTemplate;
        this.requestIdentity = requestIdentity;
    }

    public void apply(DatabaseRole role) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Database role routing requires an active synchronized transaction");
        }

        DatabaseRole existingRole =
                (DatabaseRole) TransactionSynchronizationManager.getResource(ROLE_RESOURCE_KEY);
        if (existingRole != null) {
            if (existingRole != role) {
                throw new IllegalStateException(
                        "Database role switch inside one transaction is forbidden: "
                                + existingRole.sqlName() + " -> " + role.sqlName());
            }
            assertIdentityCompatible();
            return;
        }

        jdbcTemplate.execute("set local role " + role.sqlName());
        String currentRole = jdbcTemplate.queryForObject("select current_role", String.class);
        if (!role.sqlName().equals(currentRole)) {
            throw new IllegalStateException(
                    "PostgreSQL role routing failed: expected "
                            + role.sqlName() + " but was " + currentRole);
        }

        TransactionSynchronizationManager.bindResource(ROLE_RESOURCE_KEY, role);
        try {
            Object boundIdentity = bindRequestIdentity();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void suspend() {
                    TransactionSynchronizationManager.unbindResourceIfPossible(USER_RESOURCE_KEY);
                    TransactionSynchronizationManager.unbindResourceIfPossible(ROLE_RESOURCE_KEY);
                }

                @Override
                public void resume() {
                    TransactionSynchronizationManager.bindResource(ROLE_RESOURCE_KEY, role);
                    TransactionSynchronizationManager.bindResource(USER_RESOURCE_KEY, boundIdentity);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(USER_RESOURCE_KEY);
                    TransactionSynchronizationManager.unbindResourceIfPossible(ROLE_RESOURCE_KEY);
                }
            });
        } catch (RuntimeException | Error exception) {
            TransactionSynchronizationManager.unbindResourceIfPossible(USER_RESOURCE_KEY);
            TransactionSynchronizationManager.unbindResourceIfPossible(ROLE_RESOURCE_KEY);
            throw exception;
        }
    }

    private Object bindRequestIdentity() {
        OptionalLong userId = requestIdentity.currentUserId();
        Object identity = userId.isPresent() ? userId.getAsLong() : ANONYMOUS_USER;
        String value = userId.isPresent() ? Long.toString(userId.getAsLong()) : "";
        jdbcTemplate.queryForObject(
                "select set_config('jc.current_user_id', ?, true)",
                String.class,
                value);
        TransactionSynchronizationManager.bindResource(USER_RESOURCE_KEY, identity);
        return identity;
    }

    private void assertIdentityCompatible() {
        OptionalLong requested = requestIdentity.currentUserId();
        Object bound = TransactionSynchronizationManager.getResource(USER_RESOURCE_KEY);
        if (bound == ANONYMOUS_USER && requested.isEmpty()) {
            return;
        }
        if (bound instanceof Long boundUser
                && requested.isPresent()
                && boundUser.longValue() == requested.getAsLong()) {
            return;
        }
        throw new IllegalStateException("Database request identity changed inside one transaction");
    }
}
