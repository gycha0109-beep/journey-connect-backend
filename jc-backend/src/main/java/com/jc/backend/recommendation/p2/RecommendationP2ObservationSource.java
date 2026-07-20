package com.jc.backend.recommendation.p2;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.recommendation.p2.P2EvaluationContracts.Observation;
import com.jc.recommendation.p2.P2EvaluationContracts.Variant;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP2ObservationSource {

    private static final String OBSERVATION_QUERY = """
            select
                a.assignment_id,
                a.subject_ref,
                coalesce((
                    select p.segment
                    from public.recommendation_p1_profile_snapshot p
                    where p.user_id = a.user_id
                      and p.reference_time < ?
                    order by p.reference_time desc, p.created_at desc
                    limit 1
                ), 'empty') as segment,
                a.variant,
                a.assigned_at,
                count(distinct x.exposure_id) as exposure_count,
                count(distinct x.run_id) as run_count,
                count(distinct x.run_id) filter (where r.run_status = 'fallback') as fallback_count,
                count(distinct b.event_id) filter (
                    where b.event_type in ('click','like','save','share')
                      and b.occurred_at >= x.exposed_at
                      and b.occurred_at < least(?, x.exposed_at + interval '7 days')
                ) as engagement_count,
                min(x.exposed_at) as first_exposed_at
            from public.recommendation_p2_experiment_assignment a
            left join public.recommendation_p2_experiment_exposure x
              on x.assignment_id = a.assignment_id
             and x.exposed_at >= ?
             and x.exposed_at < ?
            left join public.recommendation_run r
              on r.run_id = x.run_id
            left join public.recommendation_behavior_event b
              on b.run_id = x.run_id
             and b.user_id = a.user_id
            where a.experiment_id = ?
              and a.experiment_version = ?
              and a.assigned_at < ?
            group by a.assignment_id, a.subject_ref, a.user_id, a.variant, a.assigned_at
            order by a.assignment_id
            """;

    private final JdbcTemplate jdbc;

    public RecommendationP2ObservationSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<Observation> findObservations(
            String experimentId,
            String experimentVersion,
            Instant observedFrom,
            Instant observedTo) {
        List<Observation> observations = jdbc.query(
                OBSERVATION_QUERY,
                (rs, rowNum) -> {
                    long exposures = rs.getLong("exposure_count");
                    long runs = rs.getLong("run_count");
                    long fallbacks = rs.getLong("fallback_count");
                    long engagements = rs.getLong("engagement_count");
                    Timestamp firstExposure = rs.getTimestamp("first_exposed_at");
                    Instant occurredAt = firstExposure == null
                            ? rs.getTimestamp("assigned_at").toInstant()
                            : firstExposure.toInstant();

                    Map<String, Double> metrics = new LinkedHashMap<>();
                    metrics.put("engagement_rate", engagements > 0 ? 1.0d : 0.0d);
                    metrics.put("fallback_rate", runs == 0 ? 0.0d : (double) fallbacks / runs);
                    return new Observation(
                            rs.getString("assignment_id"),
                            rs.getString("subject_ref"),
                            rs.getString("segment"),
                            Variant.valueOf(rs.getString("variant").toUpperCase(Locale.ROOT)),
                            true,
                            exposures > 0,
                            true,
                            occurredAt,
                            metrics);
                },
                Timestamp.from(observedTo),
                Timestamp.from(observedTo),
                Timestamp.from(observedFrom),
                Timestamp.from(observedTo),
                experimentId,
                experimentVersion,
                Timestamp.from(observedTo));

        // Keep exposed observations and only recent, not-yet-exposed assignments. This prevents
        // permanently stale assignments from inflating the statistical denominator.
        return observations.stream()
                .filter(observation -> observation.exposed()
                        || (!observation.occurredAt().isBefore(observedFrom)
                        && observation.occurredAt().isBefore(observedTo)))
                .toList();
    }
}
