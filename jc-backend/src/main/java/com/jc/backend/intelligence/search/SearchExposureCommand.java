package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.util.List;

public record SearchExposureCommand(
        String schemaVersion,
        String subjectRef,
        String identityScheme,
        String sessionId,
        String searchRunId,
        String resultSnapshotRef,
        String queryFingerprint,
        String rankingPolicyVersion,
        String pageOccurrenceId,
        String visibilityRuleVersion,
        String producerBuildId,
        List<Item> items) {

    public SearchExposureCommand {
        items = List.copyOf(items);
    }

    public record Item(
            String exposureId,
            String idempotencyKey,
            long postId,
            int absoluteRank,
            int pagePosition,
            int visibleRatioBasisPoints,
            long dwellMilliseconds,
            Instant exposedAt) {}
}
