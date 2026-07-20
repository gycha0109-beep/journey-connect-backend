package com.jc.backend.search.shadow.production;

import java.util.Arrays;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class ProductionSearchShadowProfileCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return productionProfileActive(context.getEnvironment().getActiveProfiles());
    }

    public static boolean productionProfileActive(String... activeProfiles) {
        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equals(profile) || "production".equals(profile));
    }
}
