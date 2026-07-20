package com.jc.backend.recommendation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Stores immutable persistence-replay evidence used by the CANARY readiness gate. */
@Component
public class RecommendationReplayAuditStore {

    private static final String INSERT = """
            insert into public.recommendation_replay_audit (
              audit_id, run_id, evaluator_version, evaluator_build_id, replay_status,
              mismatch_categories, ranking_input_hash, result_snapshot_hash,
              expected_result_fingerprint, actual_result_fingerprint,
              ranked_candidate_count, terminal_candidate_count, duration_ms
            ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?)
            on conflict (audit_id) do nothing
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationReplayAuditStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public StoredReplayAudit store(ReplayAuditWrite write) {
        Objects.requireNonNull(write, "write");
        jdbcTemplate.update(
                INSERT,
                write.auditId(),
                write.runId(),
                write.evaluatorVersion(),
                write.evaluatorBuildId(),
                write.replayStatus(),
                json(write.mismatchCategories()),
                write.rankingInputHash(),
                write.resultSnapshotHash(),
                write.expectedResultFingerprint(),
                write.actualResultFingerprint(),
                write.rankedCandidateCount(),
                write.terminalCandidateCount(),
                write.durationMs());
        StoredReplayAudit stored = find(write.auditId())
                .orElseThrow(() -> new IllegalStateException(
                        "Replay audit was not persisted: " + write.auditId()));
        assertEquivalent(write, stored);
        return stored;
    }

    public Optional<StoredReplayAudit> find(String auditId) {
        return jdbcTemplate.query(
                """
                select audit_id, run_id, evaluator_version, evaluator_build_id,
                       replay_status, mismatch_categories::text, ranking_input_hash,
                       result_snapshot_hash, expected_result_fingerprint,
                       actual_result_fingerprint, ranked_candidate_count,
                       terminal_candidate_count, duration_ms
                from public.recommendation_replay_audit
                where audit_id = ?
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(new StoredReplayAudit(
                                resultSet.getString("audit_id"),
                                resultSet.getString("run_id"),
                                resultSet.getString("evaluator_version"),
                                resultSet.getString("evaluator_build_id"),
                                resultSet.getString("replay_status"),
                                parseList(resultSet.getString("mismatch_categories")),
                                resultSet.getString("ranking_input_hash"),
                                resultSet.getString("result_snapshot_hash"),
                                resultSet.getString("expected_result_fingerprint"),
                                resultSet.getString("actual_result_fingerprint"),
                                resultSet.getInt("ranked_candidate_count"),
                                resultSet.getInt("terminal_candidate_count"),
                                resultSet.getLong("duration_ms")))
                        : Optional.empty(),
                auditId);
    }

    public List<ReadinessEvidence> findRecentShadowEvidence(
            int limit, String evaluatorVersion, String evaluatorBuildId) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
                """
                select r.run_id, r.run_status, r.duration_ms, a.replay_status
                from public.recommendation_run r
                left join public.recommendation_replay_audit a
                  on a.run_id = r.run_id
                 and a.evaluator_version = ?
                 and a.evaluator_build_id = ?
                where r.run_mode = 'shadow'
                order by r.created_at desc, r.run_id desc
                limit ?
                """,
                (resultSet, rowNumber) -> new ReadinessEvidence(
                        resultSet.getString("run_id"),
                        resultSet.getString("run_status"),
                        resultSet.getLong("duration_ms"),
                        resultSet.getString("replay_status")),
                evaluatorVersion,
                evaluatorBuildId,
                limit);
    }

    private void assertEquivalent(ReplayAuditWrite write, StoredReplayAudit stored) {
        if (!stored.runId().equals(write.runId())
                || !stored.evaluatorVersion().equals(write.evaluatorVersion())
                || !stored.evaluatorBuildId().equals(write.evaluatorBuildId())
                || !stored.replayStatus().equals(write.replayStatus())
                || !stored.mismatchCategories().equals(write.mismatchCategories())
                || !stored.rankingInputHash().equals(write.rankingInputHash())
                || !stored.resultSnapshotHash().equals(write.resultSnapshotHash())
                || !Objects.equals(stored.expectedResultFingerprint(), write.expectedResultFingerprint())
                || !Objects.equals(stored.actualResultFingerprint(), write.actualResultFingerprint())
                || stored.rankedCandidateCount() != write.rankedCandidateCount()
                || stored.terminalCandidateCount() != write.terminalCandidateCount()) {
            throw new IllegalStateException(
                    "Replay audit ID is already bound to different evidence: " + write.auditId());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Replay audit JSON could not be encoded", exception);
        }
    }

    private List<String> parseList(String value) {
        try {
            return List.copyOf(objectMapper.readValue(
                    value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored replay categories are invalid JSON", exception);
        }
    }

    public record ReplayAuditWrite(
            String auditId,
            String runId,
            String evaluatorVersion,
            String evaluatorBuildId,
            String replayStatus,
            List<String> mismatchCategories,
            String rankingInputHash,
            String resultSnapshotHash,
            String expectedResultFingerprint,
            String actualResultFingerprint,
            int rankedCandidateCount,
            int terminalCandidateCount,
            long durationMs) {
        public ReplayAuditWrite {
            Objects.requireNonNull(auditId, "auditId");
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(evaluatorVersion, "evaluatorVersion");
            Objects.requireNonNull(evaluatorBuildId, "evaluatorBuildId");
            Objects.requireNonNull(replayStatus, "replayStatus");
            mismatchCategories = List.copyOf(mismatchCategories);
            Objects.requireNonNull(rankingInputHash, "rankingInputHash");
            Objects.requireNonNull(resultSnapshotHash, "resultSnapshotHash");
        }
    }

    public record StoredReplayAudit(
            String auditId,
            String runId,
            String evaluatorVersion,
            String evaluatorBuildId,
            String replayStatus,
            List<String> mismatchCategories,
            String rankingInputHash,
            String resultSnapshotHash,
            String expectedResultFingerprint,
            String actualResultFingerprint,
            int rankedCandidateCount,
            int terminalCandidateCount,
            long durationMs) {
    }

    public record ReadinessEvidence(
            String runId,
            String runStatus,
            long durationMs,
            String replayStatus) {
    }

}
