package com.jc.recommendation.evaluation;

import com.jc.recommendation.canonical.JsonWire;
import com.jc.recommendation.model.evaluation.RecommendationBehaviorDuplicateAudit;
import com.jc.recommendation.model.evaluation.RecommendationBehaviorDuplicateReason;
import com.jc.recommendation.model.evaluation.ResolveRecommendationBehaviorEventsResult;
import com.jc.recommendation.model.evaluation.ResolvedRecommendationBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import com.jc.recommendation.support.StrictUtcMilliseconds;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationBehaviorEventResolver {
    private static final Comparator<Prepared> ORDER = Comparator
            .comparingLong(Prepared::epoch)
            .thenComparing(item -> item.resolved().occurredAt(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparing(item -> item.resolved().eventId(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparing(item -> item.resolved().idempotencyKey(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparingInt(Prepared::index);

    public ResolveRecommendationBehaviorEventsResult resolve(List<UserBehaviorEvent> events) {
        if (events == null) {
            fail("INVALID_BEHAVIOR_EVENT", "events must be an array");
        }
        List<Prepared> prepared = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            prepared.add(prepare(index, events.get(index)));
        }
        prepared.sort(ORDER);
        Map<String, List<Prepared>> byEvent = group(prepared, item -> item.resolved().eventId());
        Map<String, List<Prepared>> byKey = group(prepared, item -> item.resolved().idempotencyKey());
        validateGroups(byEvent, "BEHAVIOR_EVENT_ID_CONFLICT", "same eventId has different canonical payload");
        validateGroups(byKey, "BEHAVIOR_IDEMPOTENCY_KEY_CONFLICT",
                "same idempotencyKey has different canonical payload");

        DisjointSet disjointSet = new DisjointSet(events.size());
        unionGroups(byEvent.values(), disjointSet);
        unionGroups(byKey.values(), disjointSet);
        Map<Integer, List<Prepared>> components = new LinkedHashMap<>();
        for (Prepared item : prepared) {
            components.computeIfAbsent(disjointSet.find(item.index()), ignored -> new ArrayList<>()).add(item);
        }

        List<ResolvedRecommendationBehaviorEvent> kept = new ArrayList<>();
        List<RecommendationBehaviorDuplicateAudit> audits = new ArrayList<>();
        for (List<Prepared> component : components.values()) {
            component.sort(ORDER);
            ResolvedRecommendationBehaviorEvent representative = component.getFirst().resolved();
            kept.add(representative);
            for (int index = 1; index < component.size(); index++) {
                ResolvedRecommendationBehaviorEvent duplicate = component.get(index).resolved();
                boolean sharedEvent = duplicate.eventId().equals(representative.eventId());
                boolean sharedKey = duplicate.idempotencyKey().equals(representative.idempotencyKey());
                if (!sharedEvent && !sharedKey) {
                    fail("AMBIGUOUS_BEHAVIOR_DUPLICATE_IDENTITY_GRAPH",
                            "transitive alias does not directly identify representative");
                }
                RecommendationBehaviorDuplicateReason reason = sharedEvent && sharedKey
                        ? RecommendationBehaviorDuplicateReason.DUPLICATE_IDEMPOTENCY_KEY_AND_EVENT_ID
                        : sharedEvent
                        ? RecommendationBehaviorDuplicateReason.DUPLICATE_EVENT_ID
                        : RecommendationBehaviorDuplicateReason.DUPLICATE_IDEMPOTENCY_KEY;
                audits.add(new RecommendationBehaviorDuplicateAudit(
                        duplicate.eventId(), duplicate.idempotencyKey(), representative.eventId(),
                        representative.idempotencyKey(), reason
                ));
            }
        }
        kept.sort(Comparator
                .comparingLong((ResolvedRecommendationBehaviorEvent event) ->
                        StrictUtcMilliseconds.parseEpochMilli(event.occurredAt(), "occurredAt"))
                .thenComparing(ResolvedRecommendationBehaviorEvent::occurredAt,
                        Utf16CodeUnitComparator.ASCENDING)
                .thenComparing(ResolvedRecommendationBehaviorEvent::eventId,
                        Utf16CodeUnitComparator.ASCENDING));
        audits.sort(Comparator
                .comparing(RecommendationBehaviorDuplicateAudit::duplicateEventId,
                        Utf16CodeUnitComparator.ASCENDING)
                .thenComparing(RecommendationBehaviorDuplicateAudit::duplicateIdempotencyKey,
                        Utf16CodeUnitComparator.ASCENDING));
        if (prepared.size() != kept.size() + audits.size()) {
            fail("INVALID_BEHAVIOR_EVENT", "count invariant");
        }
        return new ResolveRecommendationBehaviorEventsResult(
                prepared.size(), kept.size(), audits.size(), kept, audits
        );
    }

    public static String buildCanonicalSignature(
            ResolvedRecommendationBehaviorEvent event,
            UserBehaviorEventMetadata metadata
    ) {
        Map<String, Object> canonicalMetadata = new LinkedHashMap<>();
        canonicalMetadata.put("surface", metadata.surface() == null ? null : metadata.surface().wireValue());
        canonicalMetadata.put("position", metadata.position());
        canonicalMetadata.put("query", metadata.query());
        canonicalMetadata.put("dwellTimeMs", metadata.dwellTimeMs());
        canonicalMetadata.put("viewportRatio", normalizeZero(metadata.viewportRatio()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", event.userId());
        payload.put("sessionId", event.sessionId());
        payload.put("eventType", event.eventType().wireValue());
        payload.put("entityId", event.entityId());
        payload.put("recommendationRunId", event.recommendationRunId());
        payload.put("metadata", canonicalMetadata);
        payload.put("occurredAt", event.occurredAt());
        return hash(payload);
    }

    private static Prepared prepare(int index, UserBehaviorEvent event) {
        if (event == null) {
            fail("INVALID_BEHAVIOR_EVENT", "event must be a plain object");
        }
        nonblank(event.eventId(), "eventId");
        nonblank(event.idempotencyKey(), "idempotencyKey");
        nonblank(event.sessionId(), "sessionId");
        if (event.userId() != null) {
            nonblank(event.userId(), "userId");
        }
        if (event.entityId() != null) {
            nonblank(event.entityId(), "entityId");
        }
        if (event.recommendationRunId() != null) {
            nonblank(event.recommendationRunId(), "recommendationRunId");
        }
        validateMetadata(event.metadata());
        long epoch;
        try {
            epoch = StrictUtcMilliseconds.parseEpochMilli(event.occurredAt(), "occurredAt");
        } catch (IllegalArgumentException exception) {
            fail("INVALID_BEHAVIOR_EVENT", "occurredAt");
            return null;
        }
        ResolvedRecommendationBehaviorEvent resolved = new ResolvedRecommendationBehaviorEvent(
                event.eventId(), event.idempotencyKey(), event.userId(), event.sessionId(), event.eventType(),
                event.entityId(), event.recommendationRunId(), event.occurredAt()
        );
        return new Prepared(index, resolved, buildCanonicalSignature(resolved, event.metadata()), epoch);
    }

    private static void validateMetadata(UserBehaviorEventMetadata metadata) {
        if (metadata == null) {
            fail("INVALID_BEHAVIOR_EVENT", "metadata must be a plain object");
        }
        if (metadata.position() != null && metadata.position() < 1) {
            fail("INVALID_BEHAVIOR_EVENT", "metadata.position");
        }
        if (metadata.dwellTimeMs() != null && metadata.dwellTimeMs() < 0L) {
            fail("INVALID_BEHAVIOR_EVENT", "metadata.dwellTimeMs");
        }
        if (metadata.viewportRatio() != null
                && (!Double.isFinite(metadata.viewportRatio())
                || metadata.viewportRatio() < 0.0d || metadata.viewportRatio() > 1.0d)) {
            fail("INVALID_BEHAVIOR_EVENT", "metadata.viewportRatio");
        }
    }

    private static Double normalizeZero(Double value) {
        if (value == null) {
            return null;
        }
        return value == 0.0d ? 0.0d : value;
    }

    private static String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JsonWire.stringify(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static Map<String, List<Prepared>> group(
            List<Prepared> items,
            java.util.function.Function<Prepared, String> key
    ) {
        Map<String, List<Prepared>> result = new HashMap<>();
        for (Prepared item : items) {
            result.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static void validateGroups(
            Map<String, List<Prepared>> groups,
            String code,
            String detail
    ) {
        List<String> keys = new ArrayList<>(groups.keySet());
        keys.sort(Utf16CodeUnitComparator.ASCENDING);
        for (String key : keys) {
            Set<String> signatures = new HashSet<>();
            for (Prepared item : groups.get(key)) {
                signatures.add(item.signature());
            }
            if (signatures.size() > 1) {
                fail(code, detail);
            }
        }
    }

    private static void unionGroups(Iterable<List<Prepared>> groups, DisjointSet disjointSet) {
        for (List<Prepared> group : groups) {
            for (int index = 1; index < group.size(); index++) {
                disjointSet.union(group.getFirst().index(), group.get(index).index());
            }
        }
    }

    private static void nonblank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            fail("INVALID_BEHAVIOR_EVENT", label + " must be nonblank");
        }
    }

    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    private record Prepared(
            int index,
            ResolvedRecommendationBehaviorEvent resolved,
            String signature,
            long epoch
    ) {
    }

    private static final class DisjointSet {
        private final int[] parent;
        private DisjointSet(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) parent[index] = index;
        }
        private int find(int value) {
            if (parent[value] != value) parent[value] = find(parent[value]);
            return parent[value];
        }
        private void union(int left, int right) {
            int a = find(left), b = find(right);
            if (a != b) parent[b] = a;
        }
    }
}
