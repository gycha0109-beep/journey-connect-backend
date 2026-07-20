package com.jc.recommendation.state;

import com.jc.recommendation.model.event.UserBehaviorEvent;

import java.util.List;

public record ResolveStateEventsResult(
        List<UserBehaviorEvent> effectiveEvents,
        List<IgnoredStateEvent> ignoredEvents,
        List<FinalState> finalStates
) {
    public ResolveStateEventsResult {
        effectiveEvents = List.copyOf(effectiveEvents);
        ignoredEvents = List.copyOf(ignoredEvents);
        finalStates = List.copyOf(finalStates);
    }
}
