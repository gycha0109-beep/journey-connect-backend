package com.jc.backend.intelligence.search;

import com.jc.backend.intelligence.search.SearchCtrModels.Attribution;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationResult;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class SearchCtrCanonicalizer {

    private final RecommendationCanonicalPayload canonicalPayload;

    public SearchCtrCanonicalizer(RecommendationCanonicalPayload canonicalPayload) {
        this.canonicalPayload = canonicalPayload;
    }

    public CanonicalProjection encode(EvaluationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("search CTR evaluation result is required");
        }
        List<Attribution> sortedAttributions = result.attributions().stream()
                .sorted(Comparator.comparing(Attribution::clickEventId)
                        .thenComparing(Attribution::exposureId))
                .toList();
        ProjectionPayloadV1 payload = new ProjectionPayloadV1(
                result.attributedExposureCount(),
                result.attributedExposureIds().stream().sorted().toList(),
                sortedAttributions,
                result.computedAt().toString(),
                result.ctrBasisPoints(),
                result.eligibleExposureCount(),
                result.metricId(),
                result.metricVersion(),
                result.sourceMaxReceivedAt() == null ? null : result.sourceMaxReceivedAt().toString(),
                result.status(),
                result.unattributedClickEventIds().stream().sorted().toList(),
                result.windowEnd().toString(),
                result.windowStart().toString());
        RecommendationCanonicalPayload.Encoded encoded = canonicalPayload.encode(payload);
        return new CanonicalProjection(
                result,
                SearchHashing.sha256(encoded.json()),
                encoded.bytes(),
                encoded.json());
    }

    public record CanonicalProjection(
            EvaluationResult result,
            String fingerprint,
            byte[] bytes,
            String json) {
        public CanonicalProjection {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record ProjectionPayloadV1(
            int attributedExposureCount,
            List<String> attributedExposureIds,
            List<Attribution> attributions,
            String computedAt,
            Integer ctrBasisPoints,
            int eligibleExposureCount,
            String metricId,
            String metricVersion,
            String sourceMaxReceivedAt,
            String status,
            List<String> unattributedClickEventIds,
            String windowEnd,
            String windowStart) {}
}
