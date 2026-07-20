package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Stores canonical behavior event bytes with event-id idempotency. */
@Component
public class RecommendationBehaviorStore {

    private static final Pattern EVENT_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern RUN_ID_PATTERN = EVENT_ID_PATTERN;

    private static final Set<String> ENTITY_TYPES =
            Set.of("post", "journey", "place", "crew", "user");

    private static final String INSERT = """
            insert into public.recommendation_behavior_event (
              event_id, idempotency_key, schema_version, payload_fingerprint,
              canonical_payload, payload_size_bytes, user_id, session_id, run_id,
              event_type, entity_type, entity_key, source_entity_id, occurred_at, metadata
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            on conflict do nothing
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationBehaviorStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public StoreResult store(BehaviorWrite write) {
        Objects.requireNonNull(write, "write");
        validate(write);
        lockIdentity("event", write.eventId());
        lockIdentity("idempotency", write.idempotencyKey());
        verifyRunBinding(write);
        String fingerprint = RecommendationHashing.sha256(write.canonicalPayload());
        int inserted = jdbcTemplate.update(
                INSERT,
                write.eventId(),
                write.idempotencyKey(),
                write.schemaVersion(),
                fingerprint,
                write.canonicalPayload(),
                write.canonicalPayload().length,
                write.userId(),
                write.sessionId(),
                write.runId(),
                write.eventType().value(),
                write.entityType(),
                write.sourceEntityId() == null
                        ? null : write.entityType() + ":" + write.sourceEntityId(),
                write.sourceEntityId(),
                Timestamp.from(truncateToMicros(write.occurredAt())),
                json(write.metadata()));
        if (inserted == 0) {
            return assertExisting(write, fingerprint);
        }
        return StoreResult.STORED;
    }

    private StoreResult assertExisting(BehaviorWrite write, String fingerprint) {
        List<ExistingBehavior> matches = jdbcTemplate.query(
                """
                select event_id, idempotency_key, schema_version, payload_fingerprint, canonical_payload,
                       user_id, session_id, run_id, event_type, entity_type,
                       source_entity_id, occurred_at, metadata::text
                from public.recommendation_behavior_event
                where event_id = ? or idempotency_key = ?
                order by case when event_id = ? then 0 else 1 end
                """,
                (resultSet, rowNumber) -> new ExistingBehavior(
                        resultSet.getString("event_id"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("schema_version"),
                        resultSet.getString("payload_fingerprint"),
                        resultSet.getBytes("canonical_payload"),
                        nullableLong(resultSet, "user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("run_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("entity_type"),
                        nullableLong(resultSet, "source_entity_id"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("metadata")),
                write.eventId(),
                write.idempotencyKey(),
                write.eventId());
        ExistingBehavior existing = matches.isEmpty() ? null : matches.get(0);
        if (existing == null
                || !existing.eventId().equals(write.eventId())
                || !existing.idempotencyKey().equals(write.idempotencyKey())
                || !existing.schemaVersion().equals(write.schemaVersion())
                || !existing.payloadFingerprint().equals(fingerprint)
                || !Arrays.equals(existing.canonicalPayload(), write.canonicalPayload())
                || !Objects.equals(existing.userId(), write.userId())
                || !existing.sessionId().equals(write.sessionId())
                || !Objects.equals(existing.runId(), write.runId())
                || !existing.eventType().equals(write.eventType().value())
                || !Objects.equals(existing.entityType(), write.entityType())
                || !Objects.equals(existing.sourceEntityId(), write.sourceEntityId())
                || !truncateToMicros(existing.occurredAt())
                        .equals(truncateToMicros(write.occurredAt()))
                || !jsonNode(existing.metadataJson()).equals(objectMapper.valueToTree(write.metadata()))) {
            throw new BehaviorIdempotencyConflictException(write.idempotencyKey());
        }
        return StoreResult.DUPLICATE;
    }

    private void lockIdentity(String namespace, String value) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                preparedStatement -> preparedStatement.setString(
                        1,
                        "recommendation_behavior_event:" + namespace + ":" + value),
                resultSet -> { });
    }

    private void verifyRunBinding(BehaviorWrite write) {
        if (write.runId() == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.recommendation_run run
                join public.recommendation_run_candidate candidate
                  on candidate.run_id = run.run_id
                 and candidate.entity_type = ?
                 and candidate.source_entity_id = ?
                where run.run_id = ?
                  and run.user_id = ?
                  and run.session_id = ?
                """,
                Integer.class,
                write.entityType(),
                write.sourceEntityId(),
                write.runId(),
                write.userId(),
                write.sessionId());
        if (count == null || count != 1) {
            throw new BehaviorBindingException(write.runId());
        }
    }

    private void validate(BehaviorWrite write) {
        if (!EVENT_ID_PATTERN.matcher(write.eventId()).matches()) {
            throw new IllegalArgumentException("behavior event ID is invalid");
        }
        if (write.idempotencyKey().isBlank() || write.idempotencyKey().length() > 160) {
            throw new IllegalArgumentException("behavior idempotency key is invalid");
        }
        if (write.schemaVersion().isBlank() || write.schemaVersion().length() > 64) {
            throw new IllegalArgumentException("behavior schema version is invalid");
        }
        if (write.sessionId().isBlank() || write.sessionId().length() > 128) {
            throw new IllegalArgumentException("behavior session ID is invalid");
        }
        if (write.userId() != null && write.userId() <= 0) {
            throw new IllegalArgumentException("behavior user ID must be positive when present");
        }
        if (write.canonicalPayload().length > 256 * 1024) {
            throw new IllegalArgumentException("behavior payload exceeds 256 KiB");
        }
        boolean noEntity = write.entityType() == null && write.sourceEntityId() == null;
        boolean fullEntity = write.entityType() != null && write.sourceEntityId() != null;
        if (!noEntity && !fullEntity) {
            throw new IllegalArgumentException("behavior entity type and ID must be both present or absent");
        }
        if (fullEntity && (!ENTITY_TYPES.contains(write.entityType()) || write.sourceEntityId() <= 0)) {
            throw new IllegalArgumentException("behavior entity type or ID is invalid");
        }
        if (write.eventType() == BehaviorEventType.SEARCH && !noEntity) {
            throw new IllegalArgumentException("search behavior must not have an entity");
        }
        if (write.eventType() != BehaviorEventType.SEARCH && !fullEntity) {
            throw new IllegalArgumentException("non-search behavior must have an entity");
        }
        if (write.runId() != null && !RUN_ID_PATTERN.matcher(write.runId()).matches()) {
            throw new IllegalArgumentException("behavior run ID is invalid");
        }
        if (write.runId() != null && write.userId() == null) {
            throw new IllegalArgumentException("run-bound behavior must have a user");
        }
    }

    private JsonNode jsonNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted behavior metadata JSON is invalid.", exception);
        }
    }

    private static Instant truncateToMicros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Behavior metadata JSON is invalid.", exception);
        }
    }

    public enum BehaviorEventType {
        IMPRESSION("impression"), VIEW("view"), CLICK("click"), LIKE("like"),
        UNLIKE("unlike"), SAVE("save"), UNSAVE("unsave"), SHARE("share"),
        FOLLOW("follow"), UNFOLLOW("unfollow"), HIDE("hide"), REPORT("report"),
        SEARCH("search"), TAG_CLICK("tag_click"), CREW_JOIN("crew_join"),
        CREW_LEAVE("crew_leave");

        private final String value;

        BehaviorEventType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum StoreResult {
        STORED,
        DUPLICATE
    }

    public static final class BehaviorIdempotencyConflictException extends IllegalStateException {
        public BehaviorIdempotencyConflictException(String idempotencyKey) {
            super("Behavior idempotency key is bound to different content: " + idempotencyKey);
        }
    }

    public static final class BehaviorBindingException extends IllegalStateException {
        public BehaviorBindingException(String runId) {
            super("Behavior event does not match recommendation run binding: " + runId);
        }
    }

    public record BehaviorWrite(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            byte[] canonicalPayload,
            Long userId,
            String sessionId,
            String runId,
            BehaviorEventType eventType,
            String entityType,
            Long sourceEntityId,
            Instant occurredAt,
            Map<String, ?> metadata) {

        public BehaviorWrite {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload").clone();
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(occurredAt, "occurredAt");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }

    private record ExistingBehavior(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            String payloadFingerprint,
            byte[] canonicalPayload,
            Long userId,
            String sessionId,
            String runId,
            String eventType,
            String entityType,
            Long sourceEntityId,
            Instant occurredAt,
            String metadataJson) {
    }
}
