package com.jc.backend.search.shadow.production;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Keeps the legacy default-killed capability graph out of an actual production profile. */
public final class ProductionShadowDefaultCapabilityCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !ProductionSearchShadowProfileCondition.productionProfileActive(
                context.getEnvironment().getActiveProfiles());
    }
}
