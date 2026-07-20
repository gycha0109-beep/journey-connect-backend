package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.model.score.CandidateScoreHardExclusionReason;
import com.jc.recommendation.model.score.CandidateScoreNotApplicableReason;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists one immutable run and both ranked/terminal candidate partitions. */
@Component
public class RecommendationRunStore {

    private static final String INSERT_RUN = """
            insert into public.recommendation_run (
              run_id, request_id, run_mode, run_status, user_id, session_id, context_id,
              surface, reference_time, ranking_snapshot_id, metadata_snapshot_id,
              exploration_snapshot_id, result_snapshot_id, ranking_policy_version,
              base_integration_policy_version, base_ranking_policy_version,
              score_policy_version, component_policy_versions, diversity_policy_version,
              exploration_policy_version, exploration_seed, ranking_status,
              ranking_empty_reason, requested_limit, effective_limit, input_count,
              scored_candidate_count, final_ranked_candidate_count, terminal_candidate_count,
              result_fingerprint, core_build_id, duration_ms, fallback_reason
            ) values (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?,
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String INSERT_RANKED = """
            insert into public.recommendation_run_candidate (
              run_id, absolute_rank, entity_type, entity_key, source_entity_id, origin,
              score, score_is_negative_zero, base_absolute_rank, diversified_absolute_rank,
              exploration_quality_score, recent_exposure_count, seeded_tie_break_key,
              exploration_pool_rank, target_insertion_rank, score_policy_version, provenance
            ) values (?, ?, 'post', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """;

    private static final String INSERT_TERMINAL = """
            insert into public.recommendation_run_terminal_candidate (
              run_id, entity_type, entity_key, source_entity_id, score_status,
              not_applicable_reason, hard_exclusion_reason, score_policy_version, audit_payload
            ) values (?, 'post', ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationRunStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public void store(RunWrite write) {
        Objects.requireNonNull(write, "write");
        validate(write);
        jdbcTemplate.update(
                INSERT_RUN,
                write.runId(),
                write.requestId(),
                write.runMode().value(),
                write.runStatus().value(),
                write.userId(),
                write.sessionId(),
                write.contextId(),
                write.surface().value(),
                Timestamp.from(write.referenceTime()),
                write.rankingSnapshotId(),
                write.metadataSnapshotId(),
                write.explorationSnapshotId(),
                write.resultSnapshotId(),
                write.rankingPolicyVersion(),
                write.baseIntegrationPolicyVersion(),
                write.baseRankingPolicyVersion(),
                write.scorePolicyVersion(),
                json(write.componentPolicyVersions()),
                write.diversityPolicyVersion(),
                write.explorationPolicyVersion(),
                write.explorationSeed(),
                write.rankingStatus().wireValue(),
                write.rankingEmptyReason() == null ? null : write.rankingEmptyReason().wireValue(),
                write.requestedLimit(),
                write.effectiveLimit(),
                write.inputCount(),
                write.scoredCandidateCount(),
                write.rankedCandidates().size(),
                write.terminalCandidates().size(),
                write.resultFingerprint(),
                write.coreBuildId(),
                write.durationMs(),
                write.fallbackReason());
        insertRanked(write.runId(), write.rankedCandidates());
        insertTerminal(write.runId(), write.terminalCandidates());
        jdbcTemplate.execute("""
                set constraints
                  recommendation_run_candidate_partition_check,
                  recommendation_ranked_candidate_partition_check,
                  recommendation_terminal_candidate_partition_check
                immediate
                """);
        jdbcTemplate.execute("""
                set constraints
                  recommendation_run_candidate_partition_check,
                  recommendation_ranked_candidate_partition_check,
                  recommendation_terminal_candidate_partition_check
                deferred
                """);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<PersistedRankedCandidate> findRanked(String runId) {
        return jdbcTemplate.query(
                """
                select absolute_rank, source_entity_id, origin, score, score_is_negative_zero,
                       base_absolute_rank, diversified_absolute_rank, exploration_quality_score,
                       recent_exposure_count, seeded_tie_break_key, exploration_pool_rank,
                       target_insertion_rank, score_policy_version, provenance::text
                from public.recommendation_run_candidate
                where run_id = ?
                order by absolute_rank
                """,
                (resultSet, rowNumber) -> new PersistedRankedCandidate(
                        resultSet.getInt("absolute_rank"),
                        resultSet.getLong("source_entity_id"),
                        resultSet.getString("origin"),
                        nullableDouble(resultSet, "score"),
                        resultSet.getBoolean("score_is_negative_zero"),
                        nullableInteger(resultSet, "base_absolute_rank"),
                        nullableInteger(resultSet, "diversified_absolute_rank"),
                        nullableDouble(resultSet, "exploration_quality_score"),
                        nullableInteger(resultSet, "recent_exposure_count"),
                        nullableLong(resultSet, "seeded_tie_break_key"),
                        nullableInteger(resultSet, "exploration_pool_rank"),
                        nullableInteger(resultSet, "target_insertion_rank"),
                        resultSet.getString("score_policy_version"),
                        resultSet.getString("provenance")),
                runId);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<PersistedTerminalCandidate> findTerminal(String runId) {
        return jdbcTemplate.query(
                """
                select source_entity_id, score_status, not_applicable_reason,
                       hard_exclusion_reason, score_policy_version, audit_payload::text
                from public.recommendation_run_terminal_candidate
                where run_id = ?
                order by entity_key
                """,
                (resultSet, rowNumber) -> new PersistedTerminalCandidate(
                        resultSet.getLong("source_entity_id"),
                        resultSet.getString("score_status"),
                        resultSet.getString("not_applicable_reason"),
                        resultSet.getString("hard_exclusion_reason"),
                        resultSet.getString("score_policy_version"),
                        resultSet.getString("audit_payload")),
                runId);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public DeliveryContext requireDeliveryContext(String runId) {
        List<DeliveryContext> rows = jdbcTemplate.query(
                """
                select run_id, run_mode, run_status, user_id, session_id, context_id,
                       surface, reference_time, final_ranked_candidate_count
                from public.recommendation_run
                where run_id = ?
                """,
                (resultSet, rowNumber) -> new DeliveryContext(
                        resultSet.getString("run_id"),
                        resultSet.getString("run_mode"),
                        resultSet.getString("run_status"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("context_id"),
                        resultSet.getString("surface"),
                        resultSet.getTimestamp("reference_time").toInstant(),
                        resultSet.getInt("final_ranked_candidate_count")),
                runId);
        if (rows.size() != 1) {
            throw new IllegalStateException("Recommendation run is unavailable: " + runId);
        }
        return rows.get(0);
    }

    private void insertRanked(String runId, List<RankedCandidateWrite> candidates) {
        jdbcTemplate.batchUpdate(INSERT_RANKED, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                RankedCandidateWrite candidate = candidates.get(index);
                statement.setString(1, runId);
                statement.setInt(2, candidate.absoluteRank());
                statement.setString(3, entityKey(candidate.sourceEntityId()));
                statement.setLong(4, candidate.sourceEntityId());
                statement.setString(5, candidate.origin().wireValue());
                setNullableDouble(statement, 6, candidate.score());
                statement.setBoolean(7, isNegativeZero(candidate.score()));
                setNullableInteger(statement, 8, candidate.baseAbsoluteRank());
                setNullableInteger(statement, 9, candidate.diversifiedAbsoluteRank());
                setNullableDouble(statement, 10, candidate.explorationQualityScore());
                setNullableInteger(statement, 11, candidate.recentExposureCount());
                setNullableLong(statement, 12, candidate.seededTieBreakKey());
                setNullableInteger(statement, 13, candidate.explorationPoolRank());
                setNullableInteger(statement, 14, candidate.targetInsertionRank());
                statement.setString(15, candidate.scorePolicyVersion());
                statement.setString(16, json(candidate.provenance()));
            }

            @Override
            public int getBatchSize() {
                return candidates.size();
            }
        });
    }

    private void insertTerminal(String runId, List<TerminalCandidateWrite> candidates) {
        jdbcTemplate.batchUpdate(INSERT_TERMINAL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                TerminalCandidateWrite candidate = candidates.get(index);
                statement.setString(1, runId);
                statement.setString(2, entityKey(candidate.sourceEntityId()));
                statement.setLong(3, candidate.sourceEntityId());
                statement.setString(4, candidate.status().wireValue());
                statement.setString(5, candidate.notApplicableReason() == null
                        ? null : candidate.notApplicableReason().wireValue());
                statement.setString(6, candidate.hardExclusionReason() == null
                        ? null : candidate.hardExclusionReason().wireValue());
                statement.setString(7, candidate.scorePolicyVersion());
                statement.setString(8, json(candidate.auditPayload()));
            }

            @Override
            public int getBatchSize() {
                return candidates.size();
            }
        });
    }

    private void validate(RunWrite write) {
        if (write.userId() <= 0) {
            throw new IllegalArgumentException("run userId must be positive");
        }
        if (write.requestedLimit() != null && write.requestedLimit() <= 0) {
            throw new IllegalArgumentException("requestedLimit must be positive when present");
        }
        if (write.effectiveLimit() < 0 || write.inputCount() < 0
                || write.scoredCandidateCount() < 0 || write.durationMs() < 0) {
            throw new IllegalArgumentException("run counts and duration must be nonnegative");
        }
        if (write.inputCount() != write.rankedCandidates().size() + write.terminalCandidates().size()) {
            throw new IllegalArgumentException("inputCount must equal final-ranked + terminal candidates");
        }
        if (write.rankedCandidates().size() < write.scoredCandidateCount()
                || write.rankedCandidates().size() > write.inputCount()) {
            throw new IllegalArgumentException(
                    "final-ranked count must be between scored candidate count and input count");
        }
        boolean validRankingPartition = write.rankingStatus() == RankingResultStatus.EMPTY
                ? write.rankingEmptyReason() == RankingEmptyReason.NO_SCORED_CANDIDATES
                        && write.rankedCandidates().isEmpty()
                : write.rankingStatus() == RankingResultStatus.RANKED
                        && write.rankingEmptyReason() == null;
        if (!validRankingPartition) {
            throw new IllegalArgumentException("ranking status and empty reason partition is invalid");
        }
        boolean validFallbackPartition = write.runStatus() == RunStatus.FALLBACK
                ? hasText(write.fallbackReason())
                : write.fallbackReason() == null;
        if (!validFallbackPartition) {
            throw new IllegalArgumentException("run status and fallback reason partition is invalid");
        }
        if (!write.resultFingerprint().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("resultFingerprint must be lowercase SHA-256 hex");
        }

        Set<Long> sourceEntityIds = new HashSet<>();
        for (int index = 0; index < write.rankedCandidates().size(); index++) {
            RankedCandidateWrite candidate = write.rankedCandidates().get(index);
            if (candidate.absoluteRank() != index + 1) {
                throw new IllegalArgumentException("ranked candidates must have contiguous ranks");
            }
            candidate.validate();
            if (!sourceEntityIds.add(candidate.sourceEntityId())) {
                throw new IllegalArgumentException("run candidates must not contain duplicate source IDs");
            }
        }
        for (TerminalCandidateWrite candidate : write.terminalCandidates()) {
            candidate.validate();
            if (!sourceEntityIds.add(candidate.sourceEntityId())) {
                throw new IllegalArgumentException("ranked and terminal candidates must be disjoint");
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isUnitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0d && value <= 1.0d;
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Recommendation persistence JSON is invalid.", exception);
        }
    }

    private static String entityKey(long sourceEntityId) {
        return "post:" + sourceEntityId;
    }

    private static boolean isNegativeZero(Double value) {
        return value != null
                && Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
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

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record RunWrite(
            String runId,
            String requestId,
            RunMode runMode,
            RunStatus runStatus,
            long userId,
            String sessionId,
            String contextId,
            Surface surface,
            Instant referenceTime,
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String resultSnapshotId,
            String rankingPolicyVersion,
            String baseIntegrationPolicyVersion,
            String baseRankingPolicyVersion,
            String scorePolicyVersion,
            Map<String, String> componentPolicyVersions,
            String diversityPolicyVersion,
            String explorationPolicyVersion,
            String explorationSeed,
            RankingResultStatus rankingStatus,
            RankingEmptyReason rankingEmptyReason,
            Integer requestedLimit,
            int effectiveLimit,
            int inputCount,
            int scoredCandidateCount,
            String resultFingerprint,
            String coreBuildId,
            long durationMs,
            String fallbackReason,
            List<RankedCandidateWrite> rankedCandidates,
            List<TerminalCandidateWrite> terminalCandidates) {

        public RunWrite {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(runMode, "runMode");
            Objects.requireNonNull(runStatus, "runStatus");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(contextId, "contextId");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(referenceTime, "referenceTime");
            Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
            Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
            Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
            Objects.requireNonNull(resultSnapshotId, "resultSnapshotId");
            Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
            Objects.requireNonNull(baseIntegrationPolicyVersion, "baseIntegrationPolicyVersion");
            Objects.requireNonNull(baseRankingPolicyVersion, "baseRankingPolicyVersion");
            Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
            Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
            Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
            Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
            Objects.requireNonNull(explorationSeed, "explorationSeed");
            Objects.requireNonNull(rankingStatus, "rankingStatus");
            Objects.requireNonNull(resultFingerprint, "resultFingerprint");
            Objects.requireNonNull(coreBuildId, "coreBuildId");
            componentPolicyVersions = Map.copyOf(componentPolicyVersions);
            rankedCandidates = List.copyOf(Objects.requireNonNull(rankedCandidates, "rankedCandidates"));
            terminalCandidates = List.copyOf(Objects.requireNonNull(terminalCandidates, "terminalCandidates"));
        }
    }

    public record RankedCandidateWrite(
            int absoluteRank,
            long sourceEntityId,
            ExplorationCandidateOrigin origin,
            Double score,
            Integer baseAbsoluteRank,
            Integer diversifiedAbsoluteRank,
            Double explorationQualityScore,
            Integer recentExposureCount,
            Long seededTieBreakKey,
            Integer explorationPoolRank,
            Integer targetInsertionRank,
            String scorePolicyVersion,
            Map<String, ?> provenance) {

        public RankedCandidateWrite {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
            provenance = Map.copyOf(provenance);
        }

        private void validate() {
            if (absoluteRank <= 0 || sourceEntityId <= 0 || scorePolicyVersion.isBlank()) {
                throw new IllegalArgumentException("rank and source ID must be positive");
            }
            if (origin == ExplorationCandidateOrigin.PERSONALIZED) {
                if (score == null || !isUnitInterval(score)
                        || baseAbsoluteRank == null || baseAbsoluteRank <= 0
                        || diversifiedAbsoluteRank == null || diversifiedAbsoluteRank <= 0
                        || explorationQualityScore != null || recentExposureCount != null
                        || seededTieBreakKey != null || explorationPoolRank != null
                        || targetInsertionRank != null) {
                    throw new IllegalArgumentException("Invalid personalized candidate provenance");
                }
            } else if (score != null || baseAbsoluteRank != null || diversifiedAbsoluteRank != null
                    || explorationQualityScore == null || !isUnitInterval(explorationQualityScore)
                    || recentExposureCount == null || recentExposureCount < 0
                    || seededTieBreakKey == null || seededTieBreakKey < 0
                    || seededTieBreakKey > 4_294_967_295L
                    || explorationPoolRank == null || explorationPoolRank <= 0
                    || targetInsertionRank == null || targetInsertionRank <= 0) {
                throw new IllegalArgumentException("Invalid exploration candidate provenance");
            }
        }
    }

    public record TerminalCandidateWrite(
            long sourceEntityId,
            CandidateScoreStatus status,
            CandidateScoreNotApplicableReason notApplicableReason,
            CandidateScoreHardExclusionReason hardExclusionReason,
            String scorePolicyVersion,
            Map<String, ?> auditPayload) {

        public TerminalCandidateWrite {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
            auditPayload = Map.copyOf(auditPayload);
        }

        private void validate() {
            if (sourceEntityId <= 0 || scorePolicyVersion.isBlank()) {
                throw new IllegalArgumentException("terminal source ID must be positive");
            }
            boolean notApplicable = status == CandidateScoreStatus.NOT_APPLICABLE
                    && notApplicableReason != null
                    && hardExclusionReason == null;
            boolean hardExcluded = status == CandidateScoreStatus.HARD_EXCLUDED
                    && notApplicableReason == null
                    && hardExclusionReason != null;
            if (!notApplicable && !hardExcluded) {
                throw new IllegalArgumentException("Invalid terminal candidate reason partition");
            }
        }
    }

    public record PersistedTerminalCandidate(
            long sourceEntityId,
            String scoreStatus,
            String notApplicableReason,
            String hardExclusionReason,
            String scorePolicyVersion,
            String auditPayloadJson) {
    }

    public record DeliveryContext(
            String runId,
            String runMode,
            String runStatus,
            long userId,
            String sessionId,
            String contextId,
            String surface,
            Instant referenceTime,
            int rankedCandidateCount) {}

    public record PersistedRankedCandidate(
            int absoluteRank,
            long sourceEntityId,
            String origin,
            Double score,
            boolean scoreIsNegativeZero,
            Integer baseAbsoluteRank,
            Integer diversifiedAbsoluteRank,
            Double explorationQualityScore,
            Integer recentExposureCount,
            Long seededTieBreakKey,
            Integer explorationPoolRank,
            Integer targetInsertionRank,
            String scorePolicyVersion,
            String provenanceJson) {
    }
}
