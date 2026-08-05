package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.util.List;

public record SearchExposureCommand(
        String schemaVersion,
        String subjectRef,
        String identityScheme,
        String identityMappingVersion,
        String sessionId,
        String searchRunId,
        String resultSnapshotRef,
        String queryFingerprint,
        String rankingPolicyVersion,
        String pageOccurrenceId,
        String visibilityRuleVersion,
        String retentionPolicyVersion,
        String producerBuildId,
        List<Item> items) {

    public SearchExposureCommand {
        items = List.copyOf(items);
    }

    public SearchExposureCommand(
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
        this(
                schemaVersion, subjectRef, identityScheme,
                SearchExposureContract.IDENTITY_MAPPING_VERSION, sessionId,
                searchRunId, resultSnapshotRef, queryFingerprint, rankingPolicyVersion,
                pageOccurrenceId, visibilityRuleVersion,
                SearchExposureContract.RETENTION_POLICY_VERSION, producerBuildId, items);
    }

    public record Item(
            String exposureId,
            String idempotencyKey,
            long postId,
            int absoluteRank,
            int pagePosition,
            int visibleRatioBasisPoints,
            long dwellMilliseconds,
            Instant exposedAt,
            Instant retentionUntil) {

        public Item(
                String exposureId,
                String idempotencyKey,
                long postId,
                int absoluteRank,
                int pagePosition,
                int visibleRatioBasisPoints,
                long dwellMilliseconds,
                Instant exposedAt) {
            this(
                    exposureId, idempotencyKey, postId, absoluteRank, pagePosition,
                    visibleRatioBasisPoints, dwellMilliseconds, exposedAt,
                    exposedAt.plus(SearchExposureContract.RAW_RETENTION));
        }
    }
}
