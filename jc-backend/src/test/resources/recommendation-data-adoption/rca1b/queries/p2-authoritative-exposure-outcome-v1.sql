SELECT
  m.case_id,
  'recommendation_p2_experiment_exposure' AS exposure_authority,
  'experiment:' || a.experiment_id AS experiment_ref,
  a.experiment_version,
  a.variant AS variant_ref,
  m.synthetic_subject_ref AS subject_ref,
  'session:' || x.session_id AS session_ref,
  x.run_id AS run_ref,
  x.exposure_id AS exposure_ref,
  604800::bigint AS outcome_window_seconds,
  bool_or(b.event_type = 'click' AND b.occurred_at >= x.exposed_at AND b.occurred_at < x.exposed_at + interval '604800 seconds') AS clicked,
  bool_or(b.event_type = 'like'  AND b.occurred_at >= x.exposed_at AND b.occurred_at < x.exposed_at + interval '604800 seconds') AS liked,
  bool_or(b.event_type = 'save'  AND b.occurred_at >= x.exposed_at AND b.occurred_at < x.exposed_at + interval '604800 seconds') AS saved,
  bool_or(b.event_type = 'share' AND b.occurred_at >= x.exposed_at AND b.occurred_at < x.exposed_at + interval '604800 seconds') AS shared,
  (r.run_status = 'fallback') AS fallback_observed,
  m.checkpoint_ref,
  m.checkpoint_sequence,
  to_char(m.checkpoint_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS checkpoint_at,
  to_char(m.snapshot_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS snapshot_at,
  m.lineage_fingerprint,
  count(DISTINCT x.exposure_id) AS exposure_row_count,
  count(DISTINCT b.event_id) FILTER (
    WHERE b.event_type IN ('click','like','save','share')
      AND b.occurred_at >= x.exposed_at
      AND b.occurred_at < x.exposed_at + interval '604800 seconds'
  ) AS outcome_row_count
FROM rca1b_fixture.p2_case_map m
JOIN public.recommendation_p2_experiment_assignment a
  ON a.assignment_id = m.assignment_id
JOIN public.recommendation_p2_experiment_exposure x
  ON x.assignment_id = a.assignment_id
 AND x.exposure_id = m.exposure_id
JOIN public.recommendation_run r
  ON r.run_id = x.run_id
LEFT JOIN public.recommendation_behavior_event b
  ON b.run_id = x.run_id
 AND b.user_id = x.user_id
WHERE m.case_id = ?
GROUP BY m.case_id, a.experiment_id, a.experiment_version, a.variant,
         m.synthetic_subject_ref, x.session_id, x.run_id, x.exposure_id,
         r.run_status, m.checkpoint_ref, m.checkpoint_sequence,
         m.checkpoint_at, m.snapshot_at, m.lineage_fingerprint
ORDER BY a.experiment_id, a.experiment_version, x.exposure_id
LIMIT ?
