package com.jc.recommendation.exposure;

import com.jc.recommendation.model.exposure.RecommendationExposureDuplicateAudit;
import com.jc.recommendation.model.exposure.RecommendationExposureDuplicateReason;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.ResolveRecommendationExposureEventsResult;
import com.jc.recommendation.support.StrictUtcMilliseconds;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationExposureEventResolver {
    private static final Comparator<Node> NODE_ORDER = Comparator
            .comparingLong((Node node) -> StrictUtcMilliseconds.parseEpochMilli(
                    node.event().servedAt(), "servedAt"))
            .thenComparing(node -> node.event().servedAt(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparing(node -> node.event().eventId(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparing(node -> node.event().idempotencyKey(), Utf16CodeUnitComparator.ASCENDING)
            .thenComparingInt(Node::index);

    public ResolveRecommendationExposureEventsResult resolve(List<RecommendationExposureEventV1> events) {
        if (events == null) {
            fail("INVALID_EXPOSURE_EVENT", "events must be array");
        }
        List<Node> all = new ArrayList<>(events.size());
        for (int index = 0; index < events.size(); index++) {
            RecommendationExposureEventV1 event = events.get(index);
            RecommendationTraceContracts.validateEvent(event);
            all.add(new Node(index, event, RecommendationTraceCanonical.hashCanonical(
                    RecommendationTraceCanonical.eventSignatureProjection(event))));
        }

        Map<String, List<Node>> byEvent = group(all, node -> node.event().eventId());
        Map<String, List<Node>> byKey = group(all, node -> node.event().idempotencyKey());
        Map<String, List<Node>> byRun = group(all, node -> node.event().recommendationRunId());
        validateSignatureGroups(byEvent, "EVENT_ID_CONFLICT", "same eventId has different payload");
        validateSignatureGroups(byKey, "IDEMPOTENCY_KEY_CONFLICT", "same idempotencyKey has different payload");
        for (List<Node> group : byRun.values()) {
            RecommendationExposureEventV1 first = group.getFirst().event();
            for (int index = 1; index < group.size(); index++) {
                if (!sameRun(first, group.get(index).event())) {
                    fail("RECOMMENDATION_RUN_CONFLICT", "run invariant mismatch");
                }
            }
        }

        List<Node> sorted = new ArrayList<>(all);
        sorted.sort(NODE_ORDER);
        DisjointSet disjointSet = new DisjointSet(events.size());
        unionGroups(byEvent.values(), disjointSet);
        unionGroups(byKey.values(), disjointSet);

        Map<Integer, List<Node>> components = new LinkedHashMap<>();
        for (Node node : sorted) {
            int root = disjointSet.find(node.index());
            components.computeIfAbsent(root, ignored -> new ArrayList<>()).add(node);
        }

        List<RecommendationExposureEventV1> kept = new ArrayList<>();
        List<RecommendationExposureDuplicateAudit> audits = new ArrayList<>();
        for (List<Node> component : components.values()) {
            component.sort(NODE_ORDER);
            RecommendationExposureEventV1 representative = component.getFirst().event();
            kept.add(representative);
            for (int index = 1; index < component.size(); index++) {
                RecommendationExposureEventV1 duplicate = component.get(index).event();
                boolean shareEvent = duplicate.eventId().equals(representative.eventId());
                boolean shareKey = duplicate.idempotencyKey().equals(representative.idempotencyKey());
                if (!shareEvent && !shareKey) {
                    fail("AMBIGUOUS_DUPLICATE_IDENTITY_GRAPH", "duplicate alias bridge is ambiguous");
                }
                RecommendationExposureDuplicateReason reason = shareEvent && shareKey
                        ? RecommendationExposureDuplicateReason.DUPLICATE_IDEMPOTENCY_KEY_AND_EVENT_ID
                        : shareEvent
                        ? RecommendationExposureDuplicateReason.DUPLICATE_EVENT_ID
                        : RecommendationExposureDuplicateReason.DUPLICATE_IDEMPOTENCY_KEY;
                audits.add(new RecommendationExposureDuplicateAudit(
                        duplicate.eventId(), duplicate.idempotencyKey(), representative.eventId(),
                        representative.idempotencyKey(), reason
                ));
            }
        }
        kept.sort(Comparator
                .comparingLong((RecommendationExposureEventV1 event) -> StrictUtcMilliseconds.parseEpochMilli(
                        event.servedAt(), "servedAt"))
                .thenComparing(RecommendationExposureEventV1::servedAt, Utf16CodeUnitComparator.ASCENDING)
                .thenComparing(RecommendationExposureEventV1::eventId, Utf16CodeUnitComparator.ASCENDING));
        audits.sort(Comparator
                .comparing(RecommendationExposureDuplicateAudit::duplicateEventId,
                        Utf16CodeUnitComparator.ASCENDING)
                .thenComparing(RecommendationExposureDuplicateAudit::duplicateIdempotencyKey,
                        Utf16CodeUnitComparator.ASCENDING));
        return new ResolveRecommendationExposureEventsResult(
                all.size(), kept.size(), audits.size(), kept, audits
        );
    }

    private static Map<String, List<Node>> group(
            List<Node> nodes,
            java.util.function.Function<Node, String> key
    ) {
        Map<String, List<Node>> result = new HashMap<>();
        for (Node node : nodes) {
            result.computeIfAbsent(key.apply(node), ignored -> new ArrayList<>()).add(node);
        }
        return result;
    }

    private static void validateSignatureGroups(
            Map<String, List<Node>> groups,
            String code,
            String message
    ) {
        for (List<Node> group : groups.values()) {
            Set<String> signatures = new HashSet<>();
            for (Node node : group) {
                signatures.add(node.signature());
            }
            if (signatures.size() > 1) {
                fail(code, message);
            }
        }
    }

    private static void unionGroups(Iterable<List<Node>> groups, DisjointSet disjointSet) {
        for (List<Node> group : groups) {
            for (int index = 1; index < group.size(); index++) {
                disjointSet.union(group.getFirst().index(), group.get(index).index());
            }
        }
    }

    private static boolean sameRun(RecommendationExposureEventV1 left, RecommendationExposureEventV1 right) {
        return left.userId().equals(right.userId())
                && left.sessionId().equals(right.sessionId())
                && left.contextId().equals(right.contextId())
                && left.replayKey().equals(right.replayKey())
                && left.cursorVersion().equals(right.cursorVersion())
                && left.rankingSnapshotId().equals(right.rankingSnapshotId())
                && left.metadataSnapshotId().equals(right.metadataSnapshotId())
                && left.explorationSnapshotId().equals(right.explorationSnapshotId())
                && left.rankingPolicyVersion().equals(right.rankingPolicyVersion())
                && left.baseIntegrationPolicyVersion().equals(right.baseIntegrationPolicyVersion())
                && left.baseRankingPolicyVersion().equals(right.baseRankingPolicyVersion())
                && left.scorePolicyVersion().equals(right.scorePolicyVersion())
                && left.diversityPolicyVersion().equals(right.diversityPolicyVersion())
                && left.explorationPolicyVersion().equals(right.explorationPolicyVersion())
                && left.explorationSeed().equals(right.explorationSeed())
                && left.rankingStatus() == right.rankingStatus()
                && left.rankingEmptyReason() == right.rankingEmptyReason()
                && left.inputCount() == right.inputCount()
                && left.finalRankedCandidateCount() == right.finalRankedCandidateCount()
                && left.terminalCandidateCount() == right.terminalCandidateCount()
                && left.componentPolicyVersions().equals(right.componentPolicyVersions())
                && left.diversitySummary().equals(right.diversitySummary())
                && left.explorationSummary().equals(right.explorationSummary());
    }

    private static void fail(String code, String message) {
        throw new IllegalArgumentException(code + ": " + message);
    }

    private record Node(int index, RecommendationExposureEventV1 event, String signature) {
    }

    private static final class DisjointSet {
        private final int[] parent;

        private DisjointSet(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot;
            }
        }
    }
}
