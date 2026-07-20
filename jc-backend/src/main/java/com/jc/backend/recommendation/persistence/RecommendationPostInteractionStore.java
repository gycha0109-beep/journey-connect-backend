package com.jc.backend.recommendation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Applies a post state transition and its behavior event in one APP-role database transaction. */
@Component
public class RecommendationPostInteractionStore {

    private static final String APPLY = """
            select public.apply_recommendation_post_interaction(
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb)
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationPostInteractionStore(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public Result apply(InteractionWrite write) {
        Objects.requireNonNull(write, "write");
        try {
            String value = jdbcTemplate.queryForObject(
                    APPLY,
                    String.class,
                    write.userId(),
                    write.postId(),
                    write.action().value(),
                    write.eventId(),
                    write.idempotencyKey(),
                    write.schemaVersion(),
                    RecommendationHashing.sha256(write.canonicalPayload()),
                    write.canonicalPayload(),
                    write.sessionId(),
                    write.runId(),
                    Timestamp.from(write.occurredAt()),
                    json(write.metadata()));
            if (value == null) {
                throw new IllegalStateException("Atomic recommendation interaction returned null");
            }
            return Result.from(value);
        } catch (DataAccessException exception) {
            String sqlState = sqlState(exception);
            if ("42501".equals(sqlState) || "23503".equals(sqlState) || "23514".equals(sqlState)) {
                throw new InteractionBindingException(exception);
            }
            throw exception;
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Interaction metadata JSON is invalid", exception);
        }
    }

    private static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    public enum Action {
        LIKE("like"),
        UNLIKE("unlike"),
        SAVE("save"),
        UNSAVE("unsave");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum Result {
        APPLIED,
        DUPLICATE,
        NO_CHANGE,
        IDEMPOTENCY_CONFLICT;

        private static Result from(String value) {
            return switch (value) {
                case "applied" -> APPLIED;
                case "duplicate" -> DUPLICATE;
                case "no_change" -> NO_CHANGE;
                case "idempotency_conflict" -> IDEMPOTENCY_CONFLICT;
                default -> throw new IllegalStateException(
                        "Unknown atomic recommendation interaction result: " + value);
            };
        }
    }

    public record InteractionWrite(
            long userId,
            long postId,
            Action action,
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            byte[] canonicalPayload,
            String sessionId,
            String runId,
            Instant occurredAt,
            Map<String, ?> metadata) {

        public InteractionWrite {
            if (userId <= 0 || postId <= 0) {
                throw new IllegalArgumentException("interaction user and post IDs must be positive");
            }
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload").clone();
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }

    public static final class InteractionBindingException extends RuntimeException {
        public InteractionBindingException(Throwable cause) {
            super("Recommendation interaction binding is invalid", cause);
        }
    }
}
