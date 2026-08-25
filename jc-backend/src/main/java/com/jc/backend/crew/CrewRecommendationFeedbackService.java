package com.jc.backend.crew;

import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Bridges an approved crew membership into immutable recommendation behavior history. */
@Service
public class CrewRecommendationFeedbackService {

    private static final String SCHEMA_VERSION = "crew-recommendation-feedback-v1";
    private static final String POLICY_VERSION = "crew-join-positive-only-v1";

    private final RecommendationCanonicalPayload canonicalPayload;
    private final JdbcTemplate jdbcTemplate;

    public CrewRecommendationFeedbackService(
            RecommendationCanonicalPayload canonicalPayload,
            JdbcTemplate jdbcTemplate) {
        this.canonicalPayload = canonicalPayload;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordApprovedJoin(long userId, long crewId, Instant approvedAt) {
        if (userId <= 0 || crewId <= 0) {
            throw new IllegalArgumentException("crew recommendation feedback IDs must be positive");
        }
        Instant occurredAt = Objects.requireNonNull(approvedAt, "approvedAt")
                .truncatedTo(ChronoUnit.MICROS);
        String eventId = "crew-join-v1:" + userId + ":" + crewId;
        String sessionId = "crew-feedback-v1:" + userId;
        Map<String, Object> metadata = Map.of(
                "feedbackPolicyVersion", POLICY_VERSION,
                "signal", "approved_join");
        CanonicalCrewJoinEventV1 event = new CanonicalCrewJoinEventV1(
                eventId,
                eventId,
                SCHEMA_VERSION,
                userId,
                sessionId,
                "crew_join",
                "crew",
                "crew:" + crewId,
                crewId,
                occurredAt.toString(),
                metadata);
        RecommendationCanonicalPayload.Encoded encoded = canonicalPayload.encode(event);

        String result = jdbcTemplate.queryForObject(
                "select public.record_crew_join_recommendation_feedback(?, ?, ?, ?)",
                String.class,
                userId,
                crewId,
                Timestamp.from(occurredAt),
                encoded.bytes());
        if (!"stored".equals(result) && !"duplicate".equals(result)) {
            throw new IllegalStateException("Unexpected crew recommendation feedback result: " + result);
        }
    }

    private record CanonicalCrewJoinEventV1(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            long userId,
            String sessionId,
            String eventType,
            String entityType,
            String entityKey,
            long sourceEntityId,
            String occurredAt,
            Map<String, Object> metadata) {}
}
