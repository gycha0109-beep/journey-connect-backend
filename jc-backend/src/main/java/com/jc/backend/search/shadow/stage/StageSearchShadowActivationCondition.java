package com.jc.backend.search.shadow.stage;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Fail-closed condition for explicitly opted-in test/stage profiles. */
public final class StageSearchShadowActivationCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StageSearchShadowProperties.activationAllowed(context.getEnvironment())
                ? ConditionOutcome.match("approved test/stage Search shadow activation")
                : ConditionOutcome.noMatch("Search shadow remains disabled");
    }
}
