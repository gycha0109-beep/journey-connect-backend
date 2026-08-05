package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.util.List;

public final class SearchCtrModels {

    private SearchCtrModels() {}

    public record EvaluationWindow(Instant start, Instant end) {
        public EvaluationWindow {
            if (start == null || end == null || !start.isBefore(end)) {
                throw new IllegalArgumentException("search CTR window must be non-empty");
            }
        }
    }

    public record ExposureOccurrence(
            String exposureId,
            String subjectRef,
            String sessionId,
            String searchRunId,
            long postId,
            int absoluteRank,
            String queryFingerprint,
            String resultSnapshotRef,
            String rankingPolicyVersion,
            Instant exposedAt,
            Instant receivedAt) {
        public ExposureOccurrence {
            requireIdentifier(exposureId, "exposureId");
            requireIdentifier(subjectRef, "subjectRef");
            requireIdentifier(sessionId, "sessionId");
            requireIdentifier(searchRunId, "searchRunId");
            requireHash(queryFingerprint, "queryFingerprint");
            requireHash(resultSnapshotRef, "resultSnapshotRef");
            requireIdentifier(rankingPolicyVersion, "rankingPolicyVersion");
            if (postId <= 0 || absoluteRank <= 0 || exposedAt == null || receivedAt == null) {
                throw new IllegalArgumentException("search CTR exposure is invalid");
            }
        }
    }

    public record BridgedClickOccurrence(
            String clickEventId,
            String subjectRef,
            String sessionId,
            String searchRunId,
            long postId,
            int absoluteRank,
            String queryFingerprint,
            String resultSnapshotRef,
            String rankingPolicyVersion,
            Instant occurredAt,
            Instant receivedAt) {
        public BridgedClickOccurrence {
            requireIdentifier(clickEventId, "clickEventId");
            requireIdentifier(subjectRef, "subjectRef");
            requireIdentifier(sessionId, "sessionId");
            requireIdentifier(searchRunId, "searchRunId");
            requireHash(queryFingerprint, "queryFingerprint");
            requireHash(resultSnapshotRef, "resultSnapshotRef");
            requireIdentifier(rankingPolicyVersion, "rankingPolicyVersion");
            if (postId <= 0 || absoluteRank <= 0 || occurredAt == null || receivedAt == null) {
                throw new IllegalArgumentException("search CTR bridged click is invalid");
            }
        }
    }

    public record EvaluationInput(
            EvaluationWindow window,
            Instant computedAt,
            List<ExposureOccurrence> exposures,
            List<BridgedClickOccurrence> clicks) {
        public EvaluationInput {
            if (window == null || computedAt == null || exposures == null || clicks == null) {
                throw new IllegalArgumentException("search CTR evaluation input is required");
            }
            exposures = List.copyOf(exposures);
            clicks = List.copyOf(clicks);
        }
    }

    public record Attribution(
            String clickEventId,
            String exposureId,
            long elapsedMilliseconds) {}

    public record EvaluationResult(
            String metricId,
            String metricVersion,
            Instant windowStart,
            Instant windowEnd,
            String status,
            int eligibleExposureCount,
            int attributedExposureCount,
            Integer ctrBasisPoints,
            Instant computedAt,
            Instant sourceMaxReceivedAt,
            List<String> attributedExposureIds,
            List<Attribution> attributions,
            List<String> unattributedClickEventIds) {
        public EvaluationResult {
            attributedExposureIds = List.copyOf(attributedExposureIds);
            attributions = List.copyOf(attributions);
            unattributedClickEventIds = List.copyOf(unattributedClickEventIds);
        }
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    private static void requireHash(String value, String label) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }
}
