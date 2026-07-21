package com.jc.data.contract.v1.event;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EventTaxonomyRegistryV1 {
    public static final String CONTRACT_ID = "behavior-event-taxonomy-v1";

    private static final Map<EventType, EventDefinitionV1> DEFINITIONS;
    private static final Map<String, EventType> P0_WIRE_MAPPING_CANDIDATES;

    static {
        EnumMap<EventType, EventDefinitionV1> definitions = new EnumMap<>(EventType.class);
        register(definitions, EventType.POST_IMPRESSION, true,
                Set.of("surface", "position", "impressionPolicyVersion"), Set.of("episodeRef"));
        register(definitions, EventType.POST_VIEW, true, Set.of("surface"), Set.of("viewEpisodeRef"));
        register(definitions, EventType.POST_DWELL, true,
                Set.of("viewEventRef", "durationMillis"), Set.of("sequence"));
        register(definitions, EventType.POST_LIKE, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.POST_UNLIKE, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.POST_BOOKMARK, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.POST_UNBOOKMARK, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.POST_SHARE, true, Set.of("shareChannelClass"), Set.of());
        register(definitions, EventType.POST_HIDE, true, Set.of("reasonCode"), Set.of());
        register(definitions, EventType.POST_REPORT, true, Set.of("reportReasonCode"), Set.of("reportRef"));
        register(definitions, EventType.SEARCH_SUBMIT, false, Set.of("searchRunRef", "queryRef"), Set.of("surface"));
        register(definitions, EventType.SEARCH_RESULT_IMPRESSION, true,
                Set.of("searchRunRef", "position", "surface"), Set.of("episodeRef"));
        register(definitions, EventType.SEARCH_RESULT_CLICK, true,
                Set.of("searchRunRef", "position", "surface"), Set.of());
        register(definitions, EventType.RECOMMENDATION_IMPRESSION, true,
                Set.of("runRef", "absoluteRank", "surface"), Set.of("episodeRef"));
        register(definitions, EventType.RECOMMENDATION_CLICK, true,
                Set.of("runRef", "absoluteRank", "surface"), Set.of());
        register(definitions, EventType.PROFILE_VIEW, true, Set.of("surface"), Set.of());
        register(definitions, EventType.FOLLOW, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.UNFOLLOW, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.TAG_CLICK, true, Set.of("tagRef", "surface"), Set.of());
        register(definitions, EventType.CREW_JOIN, true, Set.of("stateTransitionRef"), Set.of());
        register(definitions, EventType.CREW_LEAVE, true, Set.of("stateTransitionRef"), Set.of());
        if (definitions.size() != EventType.values().length) {
            throw new IllegalStateException("taxonomy registry is incomplete");
        }
        DEFINITIONS = Collections.unmodifiableMap(definitions);

        LinkedHashMap<String, EventType> p0 = new LinkedHashMap<>();
        p0.put("impression", EventType.RECOMMENDATION_IMPRESSION);
        p0.put("view", EventType.POST_VIEW);
        p0.put("click", EventType.RECOMMENDATION_CLICK);
        p0.put("like", EventType.POST_LIKE);
        p0.put("unlike", EventType.POST_UNLIKE);
        p0.put("save", EventType.POST_BOOKMARK);
        p0.put("unsave", EventType.POST_UNBOOKMARK);
        p0.put("share", EventType.POST_SHARE);
        p0.put("hide", EventType.POST_HIDE);
        p0.put("report", EventType.POST_REPORT);
        p0.put("search", EventType.SEARCH_SUBMIT);
        p0.put("tag_click", EventType.TAG_CLICK);
        p0.put("follow", EventType.FOLLOW);
        p0.put("unfollow", EventType.UNFOLLOW);
        p0.put("crew_join", EventType.CREW_JOIN);
        p0.put("crew_leave", EventType.CREW_LEAVE);
        P0_WIRE_MAPPING_CANDIDATES = Collections.unmodifiableMap(p0);
    }

    private EventTaxonomyRegistryV1() {
    }

    public static Optional<EventDefinitionV1> definition(EventFamily family, EventType type) {
        EventDefinitionV1 definition = DEFINITIONS.get(type);
        return definition != null && definition.family() == family ? Optional.of(definition) : Optional.empty();
    }

    public static Map<EventType, EventDefinitionV1> definitions() {
        return DEFINITIONS;
    }

    public static Map<String, EventType> p0WireMappingCandidates() {
        return P0_WIRE_MAPPING_CANDIDATES;
    }

    private static void register(
            Map<EventType, EventDefinitionV1> definitions,
            EventType type,
            boolean entityRequired,
            Set<String> required,
            Set<String> optional) {
        EventDefinitionV1 previous = definitions.put(
                type, new EventDefinitionV1(EventFamily.USER_BEHAVIOR, type, required, optional, entityRequired));
        if (previous != null) {
            throw new IllegalStateException("duplicate taxonomy type: " + type.wireValue());
        }
    }
}
