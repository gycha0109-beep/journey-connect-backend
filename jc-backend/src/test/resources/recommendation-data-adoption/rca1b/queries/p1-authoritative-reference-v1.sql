SELECT
  m.case_id,
  w.window_days,
  s.segment AS exact_value,
  s.input_event_count::text AS derived_value,
  CASE WHEN pref.feature_id IS NULL THEN 'NULL' ELSE 'VALUE:' || pref.feature_id END AS null_semantics,
  COALESCE(tag_text.value, '') AS empty_semantics,
  to_char(s.accepted_behavior_weight, 'FM999999990.000000') AS numeric_value,
  to_char(s.reference_time AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS timestamp_value,
  m.checkpoint_ref,
  m.checkpoint_sequence,
  to_char(m.checkpoint_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS checkpoint_at,
  to_char(m.snapshot_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS snapshot_at,
  m.lineage_fingerprint
FROM rca1b_fixture.p1_case_map m
JOIN public.recommendation_p1_profile_snapshot s
  ON s.profile_snapshot_id = m.profile_snapshot_id
CROSS JOIN (VALUES (7), (30), (90)) AS w(window_days)
LEFT JOIN public.recommendation_user_preference pref
  ON pref.user_id = s.user_id
 AND pref.feature_id = 'theme:optional'
LEFT JOIN LATERAL (
  SELECT string_agg(t.slug, ',' ORDER BY t.sort_order, t.slug) AS value
  FROM public.tags t
  WHERE false
) tag_text ON true
WHERE m.case_id = ?
ORDER BY w.window_days, s.profile_snapshot_id
LIMIT ?
