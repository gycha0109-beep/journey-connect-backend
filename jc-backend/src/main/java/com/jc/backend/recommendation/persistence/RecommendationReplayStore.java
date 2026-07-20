package com.jc.backend.recommendation.persistence;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.PersistedRankedCandidate;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.PersistedTerminalCandidate;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore.StoredSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Loads the immutable bytes and candidate partitions required for exact replay. */
@Repository
@DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
public class RecommendationReplayStore {

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationSnapshotStore snapshots;
    private final RecommendationRunStore runs;

    public RecommendationReplayStore(
            JdbcTemplate jdbcTemplate,
            RecommendationSnapshotStore snapshots,
            RecommendationRunStore runs) {
        this.jdbcTemplate = jdbcTemplate;
        this.snapshots = snapshots;
        this.runs = runs;
    }

    public Optional<ReplayBundle> load(String runId) {
        List<RunBinding> bindings = jdbcTemplate.query(
                """
                select run_id, user_id, session_id, context_id, surface, reference_time,
                       ranking_snapshot_id, metadata_snapshot_id, exploration_snapshot_id,
                       result_snapshot_id, ranking_policy_version,
                       base_integration_policy_version, base_ranking_policy_version,
                       score_policy_version, component_policy_versions::text, diversity_policy_version,
                       exploration_policy_version, exploration_seed, result_fingerprint,
                       core_build_id
                from public.recommendation_run
                where run_id = ?
                """,
                (resultSet, rowNumber) -> new RunBinding(
                        resultSet.getString("run_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("context_id"),
                        resultSet.getString("surface"),
                        resultSet.getTimestamp("reference_time").toInstant(),
                        resultSet.getString("ranking_snapshot_id"),
                        resultSet.getString("metadata_snapshot_id"),
                        resultSet.getString("exploration_snapshot_id"),
                        resultSet.getString("result_snapshot_id"),
                        resultSet.getString("ranking_policy_version"),
                        resultSet.getString("base_integration_policy_version"),
                        resultSet.getString("base_ranking_policy_version"),
                        resultSet.getString("score_policy_version"),
                        resultSet.getString("component_policy_versions"),
                        resultSet.getString("diversity_policy_version"),
                        resultSet.getString("exploration_policy_version"),
                        resultSet.getString("exploration_seed"),
                        resultSet.getString("result_fingerprint"),
                        resultSet.getString("core_build_id")),
                runId);
        if (bindings.isEmpty()) {
            return Optional.empty();
        }
        RunBinding binding = bindings.getFirst();
        return Optional.of(new ReplayBundle(
                binding,
                requiredSnapshot(binding.rankingSnapshotId()),
                requiredSnapshot(binding.metadataSnapshotId()),
                requiredSnapshot(binding.explorationSnapshotId()),
                requiredSnapshot(binding.resultSnapshotId()),
                runs.findRanked(runId),
                runs.findTerminal(runId)));
    }

    private StoredSnapshot requiredSnapshot(String snapshotId) {
        return snapshots.find(snapshotId)
                .orElseThrow(() -> new IllegalStateException(
                        "Run references missing snapshot: " + snapshotId));
    }

    public record RunBinding(
            String runId,
            long userId,
            String sessionId,
            String contextId,
            String surface,
            Instant referenceTime,
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String resultSnapshotId,
            String rankingPolicyVersion,
            String baseIntegrationPolicyVersion,
            String baseRankingPolicyVersion,
            String scorePolicyVersion,
            String componentPolicyVersionsJson,
            String diversityPolicyVersion,
            String explorationPolicyVersion,
            String explorationSeed,
            String resultFingerprint,
            String coreBuildId) {

        public RunBinding {
            Objects.requireNonNull(runId, "runId");
        }
    }

    public record ReplayBundle(
            RunBinding run,
            StoredSnapshot rankingInput,
            StoredSnapshot diversityMetadata,
            StoredSnapshot explorationMetadata,
            StoredSnapshot rankingResult,
            List<PersistedRankedCandidate> rankedCandidates,
            List<PersistedTerminalCandidate> terminalCandidates) {

        public ReplayBundle {
            rankedCandidates = List.copyOf(rankedCandidates);
            terminalCandidates = List.copyOf(terminalCandidates);
        }
    }
}
