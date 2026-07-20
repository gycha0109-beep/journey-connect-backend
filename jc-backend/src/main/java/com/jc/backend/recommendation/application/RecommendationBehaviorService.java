package com.jc.backend.recommendation.application;

import com.jc.backend.common.DomainException;
import com.jc.backend.recommendation.api.RecommendationBehaviorDtos;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorBindingException;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorEventType;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorIdempotencyConflictException;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorWrite;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.StoreResult;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates, canonicalizes, and stores authenticated run-bound behavior events. */
@Service
public final class RecommendationBehaviorService {

    private static final String SCHEMA_VERSION = "recommendation-behavior-event-v1";
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_EVENT_AGE = Duration.ofDays(30);
    private static final Pattern METADATA_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    private static final Set<Class<?>> NUMBER_TYPES = Set.of(
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class);

    private final RecommendationCanonicalPayload canonicalPayload;
    private final RecommendationBehaviorStore behaviorStore;

    public RecommendationBehaviorService(
            RecommendationCanonicalPayload canonicalPayload,
            RecommendationBehaviorStore behaviorStore) {
        this.canonicalPayload = canonicalPayload;
        this.behaviorStore = behaviorStore;
    }

    public RecommendationBehaviorDtos.EventResponse record(
            long userId,
            String tokenId,
            RecommendationBehaviorDtos.EventRequest request) {
        if (userId <= 0) {
            throw badRequest("RECOMMENDATION_EVENT_USER_INVALID", "사용자 정보가 올바르지 않습니다.");
        }
        String sessionId = RecommendationSessionIds.fromJwt(userId, tokenId);
        Instant occurredAt = request.occurredAt().truncatedTo(ChronoUnit.MICROS);
        validateOccurredAt(occurredAt);
        Map<String, Object> metadata = validateMetadata(request.metadata());
        BehaviorEventType eventType = behaviorEventType(request.eventType());

        CanonicalBehaviorEventV1 canonical = new CanonicalBehaviorEventV1(
                request.eventId(),
                request.idempotencyKey(),
                SCHEMA_VERSION,
                userId,
                sessionId,
                request.runId(),
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
                    request.runId(),
                    eventType,
                    "post",
                    request.postId(),
                    occurredAt,
                    metadata));
            return new RecommendationBehaviorDtos.EventResponse(
                    request.eventId(),
                    result == StoreResult.STORED ? "stored" : "duplicate");
        } catch (BehaviorIdempotencyConflictException exception) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_CONFLICT",
                    "같은 멱등키가 다른 추천 행동에 이미 사용되었습니다.");
        } catch (BehaviorBindingException exception) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "RECOMMENDATION_EVENT_BINDING_INVALID",
                    "추천 실행과 사용자·세션·게시물 연결이 올바르지 않습니다.");
        }
    }

    private void validateOccurredAt(Instant occurredAt) {
        Instant now = Instant.now();
        if (occurredAt.isAfter(now.plus(MAX_FUTURE_SKEW))
                || occurredAt.isBefore(now.minus(MAX_EVENT_AGE))) {
            throw badRequest(
                    "RECOMMENDATION_EVENT_TIME_INVALID",
                    "추천 행동 발생 시각이 허용 범위를 벗어났습니다.");
        }
    }

    private Map<String, Object> validateMetadata(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        if (raw.size() > 32) {
            throw badRequest(
                    "RECOMMENDATION_EVENT_METADATA_INVALID",
                    "추천 행동 메타데이터 항목 수가 너무 많습니다.");
        }
        Map<String, Object> validated = new TreeMap<>();
        raw.forEach((key, value) -> {
            if (key == null || !METADATA_KEY.matcher(key).matches()) {
                throw badRequest(
                        "RECOMMENDATION_EVENT_METADATA_INVALID",
                        "추천 행동 메타데이터 키가 올바르지 않습니다.");
            }
            validated.put(key, validateMetadataValue(value));
        });
        return Map.copyOf(validated);
    }

    private Object validateMetadataValue(Object value) {
        if (value instanceof String text) {
            if (text.length() > 256) {
                throw badRequest(
                        "RECOMMENDATION_EVENT_METADATA_INVALID",
                        "추천 행동 메타데이터 문자열이 너무 깁니다.");
            }
            return text;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value != null && NUMBER_TYPES.contains(value.getClass())) {
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
                throw badRequest(
                        "RECOMMENDATION_EVENT_METADATA_INVALID",
                        "추천 행동 메타데이터 숫자가 올바르지 않습니다.");
            }
            if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw badRequest(
                        "RECOMMENDATION_EVENT_METADATA_INVALID",
                        "추천 행동 메타데이터 숫자가 올바르지 않습니다.");
            }
            return value;
        }
        throw badRequest(
                "RECOMMENDATION_EVENT_METADATA_INVALID",
                "추천 행동 메타데이터는 짧은 문자열·숫자·불리언만 허용합니다.");
    }

    private static BehaviorEventType behaviorEventType(RecommendationBehaviorDtos.EventType type) {
        return switch (type) {
            case VIEW -> BehaviorEventType.VIEW;
            case CLICK -> BehaviorEventType.CLICK;
            case SHARE -> BehaviorEventType.SHARE;
            case HIDE -> BehaviorEventType.HIDE;
            case REPORT -> BehaviorEventType.REPORT;
        };
    }

    private static DomainException badRequest(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record CanonicalBehaviorEventV1(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            long userId,
            String sessionId,
            String runId,
            String eventType,
            String entityType,
            String entityKey,
            long sourceEntityId,
            String occurredAt,
            Map<String, Object> metadata) {
        private CanonicalBehaviorEventV1 {
            metadata = Map.copyOf(new LinkedHashMap<>(metadata));
        }
    }
}
