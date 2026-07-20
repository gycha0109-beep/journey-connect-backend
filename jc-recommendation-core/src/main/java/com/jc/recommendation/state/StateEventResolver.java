package com.jc.recommendation.state;

import com.jc.recommendation.canonical.CanonicalJson;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.support.StrictUtc;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StateEventResolver {
    private static final Map<EventType, StateDefinition> DEFINITIONS = definitions();

    private StateEventResolver() {
    }

    public static ResolveStateEventsResult resolve(List<UserBehaviorEvent> events) {
        Objects.requireNonNull(events, "events");
        Map<String, String> seenIdempotency = new HashMap<>();
        List<UserBehaviorEvent> uniqueEvents = new ArrayList<>();
        List<IgnoredStateEvent> ignoredEvents = new ArrayList<>();

        for (UserBehaviorEvent event : events) {
            validate(event);
            String signature = CanonicalJson.stringify(event.payloadWithoutEventId());
            String existingSignature = seenIdempotency.putIfAbsent(event.idempotencyKey(), signature);
            if (existingSignature != null) {
                if (!existingSignature.equals(signature)) {
                    throw new IllegalArgumentException(
                            "Conflicting payload for idempotency key: " + event.idempotencyKey()
                    );
                }
                ignoredEvents.add(new IgnoredStateEvent(event, IgnoredStateEventReason.DUPLICATE_IDEMPOTENCY));
                continue;
            }
            uniqueEvents.add(event);
        }

        List<UserBehaviorEvent> sortedEvents = new ArrayList<>(uniqueEvents);
        sortedEvents.sort(Comparator
                .comparingLong((UserBehaviorEvent event) -> StrictUtc.parseEpochMilli(event.occurredAt(), "occurredAt"))
                .thenComparing(UserBehaviorEvent::eventId, Utf16CodeUnitComparator.ASCENDING));

        Map<String, Boolean> stateByKey = new LinkedHashMap<>();
        Map<String, StateMetadata> metadataByKey = new HashMap<>();
        List<UserBehaviorEvent> effectiveEvents = new ArrayList<>();

        for (UserBehaviorEvent event : sortedEvents) {
            validate(event);
            StateDefinition definition = DEFINITIONS.get(event.eventType());
            String key = event.userId() + ":" + event.entityId() + ":" + definition.family();
            boolean currentState = stateByKey.getOrDefault(key, false);
            if (definition.nextState() == currentState) {
                ignoredEvents.add(new IgnoredStateEvent(
                        event,
                        definition.nextState()
                                ? IgnoredStateEventReason.DUPLICATE_STATE
                                : IgnoredStateEventReason.INVALID_INVERSE_STATE
                ));
                continue;
            }
            stateByKey.put(key, definition.nextState());
            metadataByKey.put(key, new StateMetadata(key, event.userId(), event.entityId(), definition.family()));
            effectiveEvents.add(event);
        }

        List<FinalState> finalStates = stateByKey.entrySet().stream()
                .map(entry -> {
                    StateMetadata metadata = metadataByKey.get(entry.getKey());
                    if (metadata == null) {
                        throw new IllegalStateException("Missing final state metadata for key: " + entry.getKey());
                    }
                    return new FinalState(
                            metadata.key(),
                            metadata.userId(),
                            metadata.entityId(),
                            metadata.family(),
                            entry.getValue()
                    );
                })
                .sorted(Comparator.comparing(FinalState::key, Utf16CodeUnitComparator.ASCENDING))
                .toList();

        return new ResolveStateEventsResult(effectiveEvents, ignoredEvents, finalStates);
    }

    private static void validate(UserBehaviorEvent event) {
        Objects.requireNonNull(event, "event");
        if (!DEFINITIONS.containsKey(event.eventType())) {
            throw new IllegalArgumentException("Unsupported state event type: " + event.eventType().wireValue());
        }
        if (event.userId() == null || event.entityId() == null) {
            throw new IllegalArgumentException(
                    "State event " + event.eventId() + " requires userId and entityId"
            );
        }
        try {
            StrictUtc.parse(event.occurredAt(), "occurredAt");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "State event " + event.eventId() + " has invalid occurredAt",
                    exception
            );
        }
        if (event.idempotencyKey().isEmpty()) {
            throw new IllegalArgumentException(
                    "State event " + event.eventId() + " requires idempotencyKey"
            );
        }
    }

    private static Map<EventType, StateDefinition> definitions() {
        EnumMap<EventType, StateDefinition> result = new EnumMap<>(EventType.class);
        result.put(EventType.LIKE, new StateDefinition("like", true));
        result.put(EventType.UNLIKE, new StateDefinition("like", false));
        result.put(EventType.SAVE, new StateDefinition("save", true));
        result.put(EventType.UNSAVE, new StateDefinition("save", false));
        result.put(EventType.FOLLOW, new StateDefinition("follow", true));
        result.put(EventType.UNFOLLOW, new StateDefinition("follow", false));
        result.put(EventType.CREW_JOIN, new StateDefinition("crew", true));
        result.put(EventType.CREW_LEAVE, new StateDefinition("crew", false));
        return Map.copyOf(result);
    }

    private record StateDefinition(String family, boolean nextState) {
    }

    private record StateMetadata(String key, String userId, String entityId, String family) {
    }
}
