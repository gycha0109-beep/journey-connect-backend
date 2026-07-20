package com.jc.recommendation.state;

import com.jc.recommendation.model.event.UserBehaviorEvent;

import java.util.Objects;

public record IgnoredStateEvent(
        UserBehaviorEvent event,
        IgnoredStateEventReason reason
) {
    public IgnoredStateEvent {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(reason, "reason");
    }
}
