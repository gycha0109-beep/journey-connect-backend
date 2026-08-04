package com.jc.backend.intelligence.search;

import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SearchExposureCanonicalizer {

    private final RecommendationCanonicalPayload canonicalPayload;

    public SearchExposureCanonicalizer(RecommendationCanonicalPayload canonicalPayload) {
        this.canonicalPayload = canonicalPayload;
    }

    public CanonicalBatch encode(SearchExposureCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("search exposure command is required");
        }
        List<SearchExposureCommand.Item> sortedItems = command.items().stream()
                .sorted(Comparator
                        .comparingInt(SearchExposureCommand.Item::pagePosition)
                        .thenComparingInt(SearchExposureCommand.Item::absoluteRank)
                        .thenComparingLong(SearchExposureCommand.Item::postId)
                        .thenComparing(SearchExposureCommand.Item::exposureId))
                .toList();

        List<CanonicalItemPayloadV1> batchItems = sortedItems.stream()
                .map(SearchExposureCanonicalizer::batchItem)
                .toList();
        CanonicalBatchPayloadV1 batchPayload = new CanonicalBatchPayloadV1(
                command.identityScheme(),
                batchItems,
                command.pageOccurrenceId(),
                command.producerBuildId(),
                command.queryFingerprint(),
                command.rankingPolicyVersion(),
                command.resultSnapshotRef(),
                command.schemaVersion(),
                command.searchRunId(),
                command.sessionId(),
                command.subjectRef(),
                SearchExposureContract.SURFACE,
                command.visibilityRuleVersion());
        RecommendationCanonicalPayload.Encoded encodedBatch = canonicalPayload.encode(batchPayload);

        List<CanonicalItem> encodedItems = new ArrayList<>(sortedItems.size());
        for (SearchExposureCommand.Item item : sortedItems) {
            CanonicalStoredItemPayloadV1 itemPayload = new CanonicalStoredItemPayloadV1(
                    item.absoluteRank(),
                    item.dwellMilliseconds(),
                    item.exposedAt().toString(),
                    item.exposureId(),
                    command.identityScheme(),
                    item.idempotencyKey(),
                    command.pageOccurrenceId(),
                    item.pagePosition(),
                    command.producerBuildId(),
                    command.queryFingerprint(),
                    command.rankingPolicyVersion(),
                    item.postId(),
                    SearchExposureContract.RESULT_ENTITY_TYPE,
                    command.resultSnapshotRef(),
                    command.schemaVersion(),
                    command.searchRunId(),
                    command.sessionId(),
                    command.subjectRef(),
                    SearchExposureContract.SURFACE,
                    item.visibleRatioBasisPoints(),
                    command.visibilityRuleVersion());
            RecommendationCanonicalPayload.Encoded encodedItem = canonicalPayload.encode(itemPayload);
            encodedItems.add(new CanonicalItem(
                    item.exposureId(),
                    item.idempotencyKey(),
                    SearchHashing.sha256(encodedItem.json()),
                    encodedItem.bytes(),
                    encodedItem.json()));
        }

        return new CanonicalBatch(
                command.schemaVersion(),
                SearchHashing.sha256(encodedBatch.json()),
                encodedBatch.bytes(),
                encodedBatch.json(),
                encodedItems);
    }

    private static CanonicalItemPayloadV1 batchItem(SearchExposureCommand.Item item) {
        return new CanonicalItemPayloadV1(
                item.absoluteRank(),
                item.dwellMilliseconds(),
                item.exposedAt().toString(),
                item.exposureId(),
                item.idempotencyKey(),
                item.pagePosition(),
                item.postId(),
                SearchExposureContract.RESULT_ENTITY_TYPE,
                item.visibleRatioBasisPoints());
    }

    public record CanonicalBatch(
            String schemaVersion,
            String fingerprint,
            byte[] bytes,
            String json,
            List<CanonicalItem> items) {
        public CanonicalBatch {
            bytes = bytes.clone();
            items = List.copyOf(items);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record CanonicalItem(
            String exposureId,
            String idempotencyKey,
            String fingerprint,
            byte[] bytes,
            String json) {
        public CanonicalItem {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record CanonicalBatchPayloadV1(
            String identityScheme,
            List<CanonicalItemPayloadV1> items,
            String pageOccurrenceId,
            String producerBuildId,
            String queryFingerprint,
            String rankingPolicyVersion,
            String resultSnapshotRef,
            String schemaVersion,
            String searchRunId,
            String sessionId,
            String subjectRef,
            String surface,
            String visibilityRuleVersion) {}

    private record CanonicalItemPayloadV1(
            int absoluteRank,
            long dwellMilliseconds,
            String exposedAt,
            String exposureId,
            String idempotencyKey,
            int pagePosition,
            long resultEntityId,
            String resultEntityType,
            int visibleRatioBasisPoints) {}

    private record CanonicalStoredItemPayloadV1(
            int absoluteRank,
            long dwellMilliseconds,
            String exposedAt,
            String exposureId,
            String identityScheme,
            String idempotencyKey,
            String pageOccurrenceId,
            int pagePosition,
            String producerBuildId,
            String queryFingerprint,
            String rankingPolicyVersion,
            long resultEntityId,
            String resultEntityType,
            String resultSnapshotRef,
            String schemaVersion,
            String searchRunId,
            String sessionId,
            String subjectRef,
            String surface,
            int visibleRatioBasisPoints,
            String visibilityRuleVersion) {}
}
