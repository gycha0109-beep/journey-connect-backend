package com.jc.backend.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;

/**
 * Starts a transaction and switches its PostgreSQL role before business SQL is executed.
 *
 * <p>The role is restricted to {@link DatabaseRole}; arbitrary SQL identifiers cannot be supplied.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DatabaseTransactional {

    DatabaseRole role();

    boolean readOnly() default false;

    DatabasePropagation propagation() default DatabasePropagation.REQUIRED;

    Isolation isolation() default Isolation.DEFAULT;

    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;
}
