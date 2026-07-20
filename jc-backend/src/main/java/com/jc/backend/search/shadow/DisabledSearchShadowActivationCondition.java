package com.jc.backend.search.shadow;

import com.jc.backend.search.shadow.production.ProductionSearchShadowProfileCondition;
import com.jc.backend.search.shadow.stage.StageSearchShadowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Ensures the disabled bridge is omitted only for an explicitly valid test/stage activation graph. */
public final class DisabledSearchShadowActivationCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (ProductionSearchShadowProfileCondition.productionProfileActive(
                context.getEnvironment().getActiveProfiles())) {
            return ConditionOutcome.noMatch("production Search shadow operational graph");
        }
        return StageSearchShadowProperties.activationAllowed(context.getEnvironment())
                ? ConditionOutcome.noMatch("explicit test/stage Search shadow activation")
                : ConditionOutcome.match("Search shadow remains disabled");
    }
}
