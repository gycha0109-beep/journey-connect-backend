package com.jc.backend.intelligence.search;

import com.jc.backend.common.DomainException;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorEventType;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorIdempotencyConflictException;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorWrite;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.StoreResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class SearchBehaviorService {

    public static final String SCHEMA_VERSION = SearchBehaviorContract.SCHEMA_VERSION;

    private final SearchContextCodec contextCodec;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final RecommendationBehaviorStore behaviorStore;

    public SearchBehaviorService(
            SearchContextCodec contextCodec,
            RecommendationCanonicalPayload canonicalPayload,
            RecommendationBehaviorStore behaviorStore) {
        this.contextCodec = contextCodec;
        this.canonicalPayload = canonicalPayload;
        this.behaviorStore = behaviorStore;
    }

    public SearchBehaviorDtos.EventResponse record(
            long userId,
            String tokenId,
            SearchBehaviorDtos.EventRequest request) {
        if (userId <= 0) {
            throw badRequest("SEARCH_BEHAVIOR_USER_INVALID", "사용자 정보가 올바르지 않습니다.");
        }
        Instant occurredAt = request.occurredAt().truncatedTo(ChronoUnit.MICROS);
        validateOccurredAt(occurredAt);
        SearchContextCodec.ResultContext context = contextCodec.decodeResultContext(
                request.resultContextToken(),
                userId,
                Instant.now());
        if (!context.contains(request.postId(), request.absoluteRank())) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "SEARCH_RESULT_BINDING_INVALID",
                    "탐색 결과와 게시물·순위 연결이 올바르지 않습니다.");
        }

        BehaviorEventType eventType = switch (request.eventType()) {
            case IMPRESSION -> BehaviorEventType.IMPRESSION;
            case VIEW -> BehaviorEventType.VIEW;
            case CLICK -> BehaviorEventType.CLICK;
        };
        String sessionId = searchSessionId(userId, tokenId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("surface", "search");
        metadata.put("source", "search-result-api");
        metadata.put("searchRunId", context.runId());
        metadata.put("queryFingerprint", context.queryFingerprint());
        metadata.put("snapshotFingerprint", context.snapshotFingerprint());
        metadata.put("policyVersion", context.policyVersion());
        metadata.put("absoluteRank", request.absoluteRank());

        CanonicalSearchBehaviorV1 canonical = new CanonicalSearchBehaviorV1(
                request.eventId(),
                request.idempotencyKey(),
                SCHEMA_VERSION,
                userId,
                sessionId,
                context.runId(),
                eventType.value(),
                "post",
                "post:" + request.postId(),
                request.postId(),
                occurredAt.toString(),
                metadata);
        RecommendationCanonicalPayload.Encoded encoded = canonicalPayload.encode(canonical);

        try {
            StoreResult result = behaviorStore.store(new BehaviorWrite(
                    request.eventId(),
                    request.idempotencyKey(),
                    SCHEMA_VERSION,
                    encoded.bytes(),
                    userId,
                    sessionId,
                    null,
                    eventType,
                    "post",
                    request.postId(),
                    occurredAt,
                    metadata));
            return new SearchBehaviorDtos.EventResponse(
                    request.eventId(),
                    result == StoreResult.STORED ? "stored" : "duplicate");
        } catch (BehaviorIdempotencyConflictException exception) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT",
                    "같은 멱등키가 다른 탐색 행동에 이미 사용되었습니다.");
        }
    }

    private void validateOccurredAt(Instant occurredAt) {
        Instant now = Instant.now();
        if (occurredAt.isAfter(now.plus(SearchBehaviorContract.MAX_FUTURE_SKEW))
                || occurredAt.isBefore(now.minus(SearchBehaviorContract.MAX_EVENT_AGE))) {
            throw badRequest(
                    "SEARCH_BEHAVIOR_TIME_INVALID",
                    "탐색 행동 발생 시각이 허용 범위를 벗어났습니다.");
        }
    }

    private static String searchSessionId(long userId, String tokenId) {
        if (tokenId != null && tokenId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            return tokenId;
        }
        return "search-jwt:" + SearchHashing.sha256(userId + ":" + String.valueOf(tokenId))
                .substring(0, 32);
    }

    private static DomainException badRequest(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record CanonicalSearchBehaviorV1(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            long userId,
            String sessionId,
            String searchRunId,
            String eventType,
            String entityType,
            String entityKey,
            long sourceEntityId,
            String occurredAt,
            Map<String, Object> metadata) {
        private CanonicalSearchBehaviorV1 {
            metadata = Map.copyOf(new LinkedHashMap<>(metadata));
        }
    }
}
