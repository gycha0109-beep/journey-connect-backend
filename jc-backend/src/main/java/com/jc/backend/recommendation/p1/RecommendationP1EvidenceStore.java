package com.jc.backend.recommendation.p1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.recommendation.p1.evaluation.P1RankingComparison;
import com.jc.recommendation.p1.policy.P1PolicySelection;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP1EvidenceStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationP1EvidenceStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public BaselineRun requireBaselineRun(String runId) {
        List<BaselineRun> rows = jdbcTemplate.query(
                """
                select run_id, run_mode, run_status, user_id, session_id, context_id,
                       surface, reference_time, ranking_policy_version, result_fingerprint,
                       final_ranked_candidate_count
                from public.recommendation_run
                where run_id = ?
                """,
                (resultSet, rowNumber) -> new BaselineRun(
                        resultSet.getString("run_id"),
                        resultSet.getString("run_mode"),
                        resultSet.getString("run_status"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("context_id"),
                        resultSet.getString("surface"),
                        resultSet.getTimestamp("reference_time").toInstant(),
                        resultSet.getString("ranking_policy_version"),
                        resultSet.getString("result_fingerprint"),
                        resultSet.getInt("final_ranked_candidate_count")),
                runId);
        if (rows.size() != 1) {
            throw new IllegalStateException("baseline recommendation run does not exist: " + runId);
        }
        BaselineRun run = rows.getFirst();
        if (!run.runStatus().equals("succeeded") || !run.surface().equals("home")) {
            throw new IllegalStateException("baseline recommendation run is not a successful home run");
        }
        return run;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<String> findBaselineEntityIds(String runId, int limit) {
        return jdbcTemplate.query(
                """
                select source_entity_id
                from public.recommendation_run_candidate
                where run_id = ?
                order by absolute_rank
                limit ?
                """,
                (resultSet, rowNumber) -> Long.toString(resultSet.getLong("source_entity_id")),
                runId,
                limit);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public int countPriorP1Assignments(long userId, Instant before) {
        Integer value = jdbcTemplate.queryForObject(
                """
                select count(*)::integer
                from public.recommendation_p1_policy_assignment
                where user_id = ? and created_at < ?
                """,
                Integer.class,
                userId,
                Timestamp.from(before));
        return value == null ? 0 : value;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public void storeProfile(String profileSnapshotId, long userId, BehaviorProfileSnapshot profile) {
        int updated = jdbcTemplate.update(
                """
                insert into public.recommendation_p1_profile_snapshot (
                  profile_snapshot_id, user_id, reference_time, profile_policy_version,
                  feature_vocabulary_version, segment, explicit_preference_count,
                  input_event_count, accepted_event_count, ignored_event_count,
                  duplicate_event_count, accepted_behavior_weight, signal_count,
                  signals, fingerprint
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                on conflict (profile_snapshot_id) do nothing
                """,
                profileSnapshotId,
                userId,
                Timestamp.from(profile.referenceTime()),
                profile.profilePolicyVersion(),
                profile.featureVocabularyVersion(),
                profile.segment().wireValue(),
                profile.explicitPreferenceCount(),
                profile.inputEventCount(),
                profile.acceptedEventCount(),
                profile.ignoredEventCount(),
                profile.duplicateEventCount(),
                profile.acceptedBehaviorWeight(),
                profile.signals().size(),
                json(profile.signals()),
                profile.fingerprint());
        if (updated == 0) {
            String existing = jdbcTemplate.queryForObject(
                    "select fingerprint from public.recommendation_p1_profile_snapshot where profile_snapshot_id = ?",
                    String.class,
                    profileSnapshotId);
            if (!Objects.equals(existing, profile.fingerprint())) {
                throw new IllegalStateException("profile snapshot ID is bound to different content");
            }
        }
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public void storeAssignment(
            String assignmentId,
            String baselineRunId,
            String treatmentRunId,
            long userId,
            String sessionId,
            String profileSnapshotId,
            String releaseId,
            P1PolicySelection selection) {
        jdbcTemplate.update(
                """
                insert into public.recommendation_p1_policy_assignment (
                  assignment_id, baseline_run_id, treatment_run_id, user_id, session_id,
                  profile_snapshot_id, release_id, experiment_assignment, segment,
                  selection_reasons, profile_policy_version, feature_vocabulary_version,
                  retrieval_policy_version, policy_bundle_version, score_policy_version,
                  diversity_policy_version, low_exposure_policy_version,
                  exploration_policy_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                assignmentId,
                baselineRunId,
                treatmentRunId,
                userId,
                sessionId,
                profileSnapshotId,
                releaseId,
                selection.assignment().wireValue(),
                selection.policyBundle().segment().wireValue(),
                json(selection.reasons()),
                selection.policyBundle().profilePolicyVersion(),
                selection.policyBundle().featureVocabularyVersion(),
                selection.policyBundle().retrievalPolicyVersion(),
                selection.policyBundle().policyBundleVersion(),
                selection.policyBundle().scorePolicy().policyVersion(),
                selection.policyBundle().diversityPolicy().policyVersion(),
                selection.policyBundle().lowExposurePolicyVersion(),
                selection.policyBundle().explorationPolicyVersion());
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public void storeComparison(
            String comparisonId,
            String baselineRunId,
            String treatmentRunId,
            String baselineFingerprint,
            String treatmentFingerprint,
            P1RankingComparison comparison) {
        jdbcTemplate.update(
                """
                insert into public.recommendation_p1_comparison (
                  comparison_id, baseline_run_id, treatment_run_id,
                  baseline_result_fingerprint, treatment_result_fingerprint,
                  baseline_policy_version, treatment_policy_version, cutoff,
                  baseline_count, treatment_count, overlap_count, overlap_rate,
                  mean_absolute_rank_displacement, treatment_unique_author_count,
                  treatment_unique_region_count, treatment_unique_theme_count,
                  treatment_low_exposure_share, treatment_top_author_share,
                  treatment_top_region_share, treatment_mean_adjusted_popularity,
                  comparison_fingerprint
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                comparisonId,
                baselineRunId,
                treatmentRunId,
                baselineFingerprint,
                treatmentFingerprint,
                comparison.baselinePolicyVersion(),
                comparison.treatmentPolicyVersion(),
                comparison.cutoff(),
                comparison.baselineCount(),
                comparison.treatmentCount(),
                comparison.overlapCount(),
                comparison.overlapRate(),
                comparison.meanAbsoluteRankDisplacement(),
                comparison.treatmentUniqueAuthorCount(),
                comparison.treatmentUniqueRegionCount(),
                comparison.treatmentUniqueThemeCount(),
                comparison.treatmentLowExposureShare(),
                comparison.treatmentTopAuthorShare(),
                comparison.treatmentTopRegionShare(),
                comparison.treatmentMeanAdjustedPopularity(),
                comparison.fingerprint());
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public PersistedTreatment requireTreatment(String treatmentRunId) {
        List<PersistedTreatment> rows = jdbcTemplate.query(
                """
                select r.result_fingerprint, r.final_ranked_candidate_count,
                       a.policy_bundle_version, a.profile_snapshot_id,
                       c.comparison_fingerprint
                from public.recommendation_run r
                join public.recommendation_p1_policy_assignment a
                  on a.treatment_run_id = r.run_id
                join public.recommendation_p1_comparison c
                  on c.treatment_run_id = r.run_id
                where r.run_id = ?
                """,
                (resultSet, rowNumber) -> new PersistedTreatment(
                        resultSet.getString("result_fingerprint"),
                        resultSet.getInt("final_ranked_candidate_count"),
                        resultSet.getString("policy_bundle_version"),
                        resultSet.getString("profile_snapshot_id"),
                        resultSet.getString("comparison_fingerprint")),
                treatmentRunId);
        if (rows.size() != 1) {
            throw new IllegalStateException("P1 treatment evidence is incomplete");
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("P1 evidence JSON is invalid", exception);
        }
    }

    public record BaselineRun(
            String runId,
            String runMode,
            String runStatus,
            long userId,
            String sessionId,
            String contextId,
            String surface,
            Instant referenceTime,
            String rankingPolicyVersion,
            String resultFingerprint,
            int rankedCandidateCount) {
    }

    public record PersistedTreatment(
            String resultFingerprint,
            int rankedCandidateCount,
            String policyBundleVersion,
            String profileSnapshotId,
            String comparisonFingerprint) {
    }
}
