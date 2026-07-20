package com.jc.backend.recommendation.p2;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import com.jc.recommendation.p2.P2EvaluationContracts.Variant;
import com.jc.recommendation.p2.P2ExperimentAssigner.Assignment;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP2AssignmentStore {

    private static final String INSERT_ASSIGNMENT =
            "insert into public.recommendation_p2_experiment_assignment(" +
                    "assignment_id,experiment_id,experiment_version,subject_ref,user_id," +
                    "assignment_unit,variant,bucket,assignment_fingerprint,assigned_at,producer_build_id" +
                    ") values(?,?,?,?,?,'user',?,?,?,?,?) " +
                    "on conflict(experiment_id,experiment_version,subject_ref) do nothing";

    private static final String SELECT_ASSIGNMENT =
            "select assignment_id,experiment_id,experiment_version,subject_ref,variant,bucket," +
                    "assignment_fingerprint from public.recommendation_p2_experiment_assignment " +
                    "where experiment_id=? and experiment_version=? and subject_ref=?";

    private static final String INSERT_EXPOSURE =
            "insert into public.recommendation_p2_experiment_exposure(" +
                    "exposure_id,assignment_id,run_id,user_id,session_id,variant,exposed_at,exposure_fingerprint" +
                    ") values(?,?,?,?,?,?,?,?) on conflict(assignment_id,run_id) do nothing";

    private final JdbcTemplate jdbc;

    public RecommendationP2AssignmentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public Assignment store(Assignment assignment, long userId, Instant assignedAt, String buildId) {
        int inserted = jdbc.update(
                INSERT_ASSIGNMENT,
                assignment.assignmentId(),
                assignment.experimentId(),
                assignment.experimentVersion(),
                assignment.subjectRef(),
                userId,
                assignment.variant().wireValue(),
                assignment.bucket(),
                assignment.fingerprint(),
                Timestamp.from(assignedAt),
                buildId);
        if (inserted == 1) {
            return assignment;
        }

        List<Assignment> existing = jdbc.query(
                SELECT_ASSIGNMENT,
                (rs, rowNum) -> new Assignment(
                        rs.getString("assignment_id"),
                        rs.getString("experiment_id"),
                        rs.getString("experiment_version"),
                        rs.getString("subject_ref"),
                        Variant.valueOf(rs.getString("variant").toUpperCase(Locale.ROOT)),
                        rs.getInt("bucket"),
                        rs.getString("assignment_fingerprint")),
                assignment.experimentId(),
                assignment.experimentVersion(),
                assignment.subjectRef());

        if (existing.size() != 1 || !existing.getFirst().equals(assignment)) {
            throw new IllegalStateException("experiment assignment binding conflict");
        }
        return existing.getFirst();
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public String storeExposure(
            Assignment assignment,
            String runId,
            long userId,
            String sessionId,
            Instant exposedAt) {
        String fingerprint = RecommendationHashing.sha256(
                (assignment.assignmentId() + "|" + runId + "|" + sessionId)
                        .getBytes(StandardCharsets.UTF_8));
        String exposureId = "p2-exposure:" + UUID.randomUUID();
        int inserted = jdbc.update(
                INSERT_EXPOSURE,
                exposureId,
                assignment.assignmentId(),
                runId,
                userId,
                sessionId,
                assignment.variant().wireValue(),
                Timestamp.from(exposedAt),
                fingerprint);
        if (inserted == 1) {
            return exposureId;
        }

        Map<String, Object> existing = jdbc.queryForMap(
                "select exposure_id, exposure_fingerprint " +
                        "from public.recommendation_p2_experiment_exposure " +
                        "where assignment_id=? and run_id=?",
                assignment.assignmentId(),
                runId);
        if (!Objects.equals(existing.get("exposure_fingerprint"), fingerprint)) {
            throw new IllegalStateException("experiment exposure binding conflict");
        }
        return (String) existing.get("exposure_id");
    }
}
