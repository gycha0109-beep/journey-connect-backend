package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists one exposure page and verifies idempotent retries against immutable bytes. */
@Component
public class RecommendationExposureStore {

    private static final String INSERT_EVENT = """
            insert into public.recommendation_exposure_event (
              event_id, idempotency_key, schema_version, payload_fingerprint,
              canonical_payload, payload_size_bytes, run_id, user_id, session_id,
              context_id, surface, served_at, replay_key, page_fingerprint,
              cursor_version, page_start_rank, page_end_rank, page_candidate_count,
              has_next_page
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (idempotency_key) do nothing
            """;

    private static final String INSERT_CANDIDATE = """
            insert into public.recommendation_exposure_candidate (
              exposure_event_id, absolute_rank, page_position, entity_type, entity_key,
              source_entity_id, origin, score, score_is_negative_zero, provenance
            ) values (?, ?, ?, 'post', ?, ?, ?, ?, ?, cast(? as jsonb))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationExposureStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public void store(ExposureWrite write) {
        Objects.requireNonNull(write, "write");
        validate(write);
        String fingerprint = RecommendationHashing.sha256(write.canonicalPayload());
        int inserted = jdbcTemplate.update(
                INSERT_EVENT,
                write.eventId(),
                write.idempotencyKey(),
                write.schemaVersion(),
                fingerprint,
                write.canonicalPayload(),
                write.canonicalPayload().length,
                write.runId(),
                write.userId(),
                write.sessionId(),
                write.contextId(),
                write.surface().value(),
                Timestamp.from(truncateToMicros(write.servedAt())),
                write.replayKey(),
                write.pageFingerprint(),
                write.cursorVersion(),
                write.pageStartRank(),
                write.pageEndRank(),
                write.candidates().size(),
                write.hasNextPage());
        if (inserted == 0) {
            assertExisting(write, fingerprint);
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_CANDIDATE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                ExposureCandidateWrite candidate = write.candidates().get(index);
                statement.setString(1, write.eventId());
                statement.setInt(2, candidate.absoluteRank());
                statement.setInt(3, index + 1);
                statement.setString(4, entityKey(candidate.sourceEntityId()));
                statement.setLong(5, candidate.sourceEntityId());
                statement.setString(6, candidate.origin().wireValue());
                if (candidate.score() == null) {
                    statement.setNull(7, java.sql.Types.DOUBLE);
                } else {
                    statement.setDouble(7, candidate.score());
                }
                statement.setBoolean(8, isNegativeZero(candidate.score()));
                statement.setString(9, json(candidate.provenance()));
            }

            @Override
            public int getBatchSize() {
                return write.candidates().size();
            }
        });
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public Instant findServedAtByIdempotencyKey(String idempotencyKey) {
        List<Instant> rows = jdbcTemplate.query(
                "select served_at from public.recommendation_exposure_event where idempotency_key = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp("served_at").toInstant(),
                idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void assertExisting(ExposureWrite write, String fingerprint) {
        ExistingExposure existing = jdbcTemplate.queryForObject(
                """
                select event_id, idempotency_key, schema_version, payload_fingerprint, canonical_payload,
                       run_id, user_id, session_id, context_id, surface, served_at,
                       replay_key, page_fingerprint, cursor_version, page_start_rank,
                       page_end_rank, page_candidate_count, has_next_page
                from public.recommendation_exposure_event
                where idempotency_key = ?
                """,
                (resultSet, rowNumber) -> new ExistingExposure(
                        resultSet.getString("event_id"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("schema_version"),
                        resultSet.getString("payload_fingerprint"),
                        resultSet.getBytes("canonical_payload"),
                        resultSet.getString("run_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("context_id"),
                        resultSet.getString("surface"),
                        resultSet.getTimestamp("served_at").toInstant(),
                        resultSet.getString("replay_key"),
                        resultSet.getString("page_fingerprint"),
                        resultSet.getString("cursor_version"),
                        nullableInteger(resultSet, "page_start_rank"),
                        nullableInteger(resultSet, "page_end_rank"),
                        resultSet.getInt("page_candidate_count"),
                        resultSet.getBoolean("has_next_page")),
                write.idempotencyKey());
        List<ExistingExposureCandidate> candidates = jdbcTemplate.query(
                """
                select absolute_rank, page_position, source_entity_id, origin, score,
                       score_is_negative_zero, provenance::text
                from public.recommendation_exposure_candidate
                where exposure_event_id = ?
                order by page_position
                """,
                (resultSet, rowNumber) -> new ExistingExposureCandidate(
                        resultSet.getInt("absolute_rank"),
                        resultSet.getInt("page_position"),
                        resultSet.getLong("source_entity_id"),
                        resultSet.getString("origin"),
                        nullableDouble(resultSet, "score"),
                        resultSet.getBoolean("score_is_negative_zero"),
                        resultSet.getString("provenance")),
                existing == null ? write.eventId() : existing.eventId());
        boolean eventMatches = existing != null
                && existing.idempotencyKey().equals(write.idempotencyKey())
                && existing.schemaVersion().equals(write.schemaVersion())
                && existing.payloadFingerprint().equals(fingerprint)
                && Arrays.equals(existing.canonicalPayload(), write.canonicalPayload())
                && existing.runId().equals(write.runId())
                && existing.userId() == write.userId()
                && existing.sessionId().equals(write.sessionId())
                && existing.contextId().equals(write.contextId())
                && existing.surface().equals(write.surface().value())
                && truncateToMicros(existing.servedAt()).equals(truncateToMicros(write.servedAt()))
                && existing.replayKey().equals(write.replayKey())
                && existing.pageFingerprint().equals(write.pageFingerprint())
                && existing.cursorVersion().equals(write.cursorVersion())
                && Objects.equals(existing.pageStartRank(), write.pageStartRank())
                && Objects.equals(existing.pageEndRank(), write.pageEndRank())
                && existing.pageCandidateCount() == write.candidates().size()
                && existing.hasNextPage() == write.hasNextPage();
        if (!eventMatches || candidates.size() != write.candidates().size()) {
            throw conflict(write.eventId());
        }
        for (int index = 0; index < candidates.size(); index++) {
            ExistingExposureCandidate existingCandidate = candidates.get(index);
            ExposureCandidateWrite expected = write.candidates().get(index);
            boolean candidateMatches = existingCandidate.absoluteRank() == expected.absoluteRank()
                    && existingCandidate.pagePosition() == index + 1
                    && existingCandidate.sourceEntityId() == expected.sourceEntityId()
                    && existingCandidate.origin().equals(expected.origin().wireValue())
                    && sameDouble(existingCandidate.score(), expected.score())
                    && existingCandidate.scoreIsNegativeZero() == isNegativeZero(expected.score())
                    && jsonNode(existingCandidate.provenanceJson())
                            .equals(objectMapper.valueToTree(expected.provenance()));
            if (!candidateMatches) {
                throw conflict(write.eventId());
            }
        }
    }

    private void validate(ExposureWrite write) {
        if (write.userId() <= 0 || write.canonicalPayload().length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("exposure user ID or payload size is invalid");
        }
        if (write.candidates().isEmpty()) {
            if (write.pageStartRank() != null || write.pageEndRank() != null) {
                throw new IllegalArgumentException("empty exposure page must not have rank bounds");
            }
            return;
        }
        if (write.pageStartRank() == null || write.pageStartRank() <= 0
                || write.pageEndRank() == null || write.pageEndRank() <= 0
                || write.pageEndRank() - write.pageStartRank() + 1 != write.candidates().size()) {
            throw new IllegalArgumentException("exposure page rank bounds do not match candidates");
        }
        Set<Long> sourceIds = new HashSet<>();
        for (int index = 0; index < write.candidates().size(); index++) {
            ExposureCandidateWrite candidate = write.candidates().get(index);
            if (candidate.absoluteRank() != write.pageStartRank() + index) {
                throw new IllegalArgumentException("exposure candidates must match contiguous page ranks");
            }
            if (candidate.sourceEntityId() <= 0 || !sourceIds.add(candidate.sourceEntityId())) {
                throw new IllegalArgumentException("exposure candidate IDs must be positive and unique");
            }
            if (candidate.origin() == ExplorationCandidateOrigin.PERSONALIZED
                    && (candidate.score() == null || !isUnitInterval(candidate.score()))) {
                throw new IllegalArgumentException("personalized exposure candidate requires unit score");
            }
            if (candidate.origin() == ExplorationCandidateOrigin.EXPLORATION
                    && candidate.score() != null) {
                throw new IllegalArgumentException("exploration exposure candidate must omit score");
            }
        }
    }

    private IllegalStateException conflict(String eventId) {
        return new IllegalStateException(
                "Exposure event ID is already bound to different content: " + eventId);
    }

    private JsonNode jsonNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted exposure provenance JSON is invalid.", exception);
        }
    }

    private static Instant truncateToMicros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean sameDouble(Double first, Double second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.doubleValue() == second.doubleValue();
    }

    private static boolean isUnitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0d && value <= 1.0d;
    }

    private static Double nullableDouble(java.sql.ResultSet resultSet, String column)
            throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column)
            throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Exposure provenance JSON is invalid.", exception);
        }
    }

    private static String entityKey(long sourceEntityId) {
        return "post:" + sourceEntityId;
    }

    private static boolean isNegativeZero(Double value) {
        return value != null
                && Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    public record ExposureWrite(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            byte[] canonicalPayload,
            String runId,
            long userId,
            String sessionId,
            String contextId,
            Surface surface,
            java.time.Instant servedAt,
            String replayKey,
            String pageFingerprint,
            String cursorVersion,
            Integer pageStartRank,
            Integer pageEndRank,
            boolean hasNextPage,
            List<ExposureCandidateWrite> candidates) {

        public ExposureWrite {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload").clone();
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(servedAt, "servedAt");
            Objects.requireNonNull(replayKey, "replayKey");
            Objects.requireNonNull(pageFingerprint, "pageFingerprint");
            Objects.requireNonNull(cursorVersion, "cursorVersion");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }

        @Override
        public byte[] canonicalPayload() {
            return canonicalPayload.clone();
        }
    }

    public record ExposureCandidateWrite(
            int absoluteRank,
            long sourceEntityId,
            ExplorationCandidateOrigin origin,
            Double score,
            Map<String, ?> provenance) {

        public ExposureCandidateWrite {
            Objects.requireNonNull(origin, "origin");
            provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
        }
    }

    private record ExistingExposure(
            String eventId,
            String idempotencyKey,
            String schemaVersion,
            String payloadFingerprint,
            byte[] canonicalPayload,
            String runId,
            long userId,
            String sessionId,
            String contextId,
            String surface,
            Instant servedAt,
            String replayKey,
            String pageFingerprint,
            String cursorVersion,
            Integer pageStartRank,
            Integer pageEndRank,
            int pageCandidateCount,
            boolean hasNextPage) {
    }

    private record ExistingExposureCandidate(
            int absoluteRank,
            int pagePosition,
            long sourceEntityId,
            String origin,
            Double score,
            boolean scoreIsNegativeZero,
            String provenanceJson) {
    }
}
