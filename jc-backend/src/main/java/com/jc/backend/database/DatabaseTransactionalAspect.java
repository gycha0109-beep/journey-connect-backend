package com.jc.backend.database;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

/** Executes role-scoped transactions without exposing unrestricted transaction annotations to services. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class DatabaseTransactionalAspect {

    private final PlatformTransactionManager transactionManager;
    private final DatabaseRoleBoundary roleBoundary;

    public DatabaseTransactionalAspect(
            PlatformTransactionManager transactionManager,
            DatabaseRoleBoundary roleBoundary) {
        this.transactionManager = transactionManager;
        this.roleBoundary = roleBoundary;
    }

    @Around("@within(com.jc.backend.database.DatabaseTransactional) || "
            + "@annotation(com.jc.backend.database.DatabaseTransactional)")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        DatabaseTransactional settings = resolve(joinPoint);
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName(joinPoint.getSignature().toShortString());
        definition.setReadOnly(settings.readOnly());
        definition.setPropagationBehavior(settings.propagation().value());
        definition.setIsolationLevel(settings.isolation().value());
        definition.setTimeout(settings.timeout());

        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        try {
            return template.execute(status -> {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    roleBoundary.apply(settings.role());
                }
                try {
                    return joinPoint.proceed();
                } catch (RuntimeException | Error exception) {
                    throw exception;
                } catch (Throwable throwable) {
                    throw new CheckedInvocationException(throwable);
                }
            });
        } catch (CheckedInvocationException exception) {
            throw exception.getCause();
        }
    }

    private DatabaseTransactional resolve(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = ClassUtils.getUserClass(joinPoint.getTarget());
        Method method = ClassUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        DatabaseTransactional methodSettings =
                AnnotatedElementUtils.findMergedAnnotation(method, DatabaseTransactional.class);
        if (methodSettings != null) {
            return methodSettings;
        }
        DatabaseTransactional classSettings =
                AnnotatedElementUtils.findMergedAnnotation(targetClass, DatabaseTransactional.class);
        if (classSettings == null) {
            throw new IllegalStateException("DatabaseTransactional metadata was not found");
        }
        return classSettings;
    }

    private static final class CheckedInvocationException extends RuntimeException {
        private CheckedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
