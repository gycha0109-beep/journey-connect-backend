package com.jc.backend.search.shadow.production;

import java.util.Arrays;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Test-only capability condition. Production profile always wins and blocks the graph. */
public final class ProductionShadowTechnicalCapabilityCondition implements Condition {
    public static final String PROFILE = "search-shadow-technical-test";
    public static final String ALLOW_PROPERTY = "search.shadow.production.technical.explicit-allow";
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod") || profiles.contains("production")) return false;
        if (!profiles.contains(PROFILE)) return false;
        return "true".equals(environment.getProperty(ALLOW_PROPERTY));
    }
}
