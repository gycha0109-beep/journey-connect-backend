#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# SQL 47 is rollback-only. Reuse only its protected DP-5 fixture section and commit
# that synthetic fixture for the concurrency probe.
awk '
  /^-- DP-6 valid FULL validation/ { print "COMMIT;"; exit }
  { print }
' "$ROOT/database/journey-connect-db-v2.7/47_data_quality_validation.sql" > "$work/setup.sql"
psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -f "$work/setup.sql" >/dev/null

psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 <<'SQL'
CREATE OR REPLACE FUNCTION public.dp6_concurrency_attempt(p_run_ref varchar)
RETURNS varchar LANGUAGE plpgsql SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_policy public.data_quality_policy_evidence_v1%ROWTYPE;
  v_input varchar(64);
  v_checks jsonb:='[]'::jsonb;
  v_metrics jsonb:='[]'::jsonb;
  v_item jsonb;
  v_code text;
  v_scope text;
  v_status text;
  v_reason text;
  v_threshold numeric;
  v_operator text;
  v_numerator bigint;
  v_value numeric;
  v_verdict jsonb;
  v_rebuild jsonb;
  v_record_fps jsonb;
  v_passed bigint;
  v_disposition varchar;
BEGIN
  SELECT * INTO STRICT v_snapshot
  FROM public.data_projection_snapshot_v1
  WHERE snapshot_ref='snapshot:dp5-profile';
  SELECT * INTO STRICT v_policy
  FROM public.data_quality_policy_evidence_v1
  WHERE quality_policy_version='data-quality-policy-v1';
  v_input:=public.data_quality_validation_input_fingerprint_v1(
    v_snapshot.snapshot_id,'FULL','data-quality-validator-v1','data-quality-policy-v1');

  FOR v_code IN SELECT jsonb_array_elements_text(v_policy.required_checks) LOOP
    v_scope:=CASE split_part(v_code,'.',1)
      WHEN 'source' THEN 'SOURCE_COMPLETENESS'
      WHEN 'projection' THEN 'PROJECTION_COMPLETENESS'
      WHEN 'snapshot' THEN 'SNAPSHOT_CONSISTENCY'
      WHEN 'lineage' THEN 'LINEAGE_INTEGRITY'
      WHEN 'identity' THEN 'IDENTITY_INTEGRITY'
      WHEN 'exposure' THEN 'EXPOSURE_INTEGRITY'
      ELSE 'DETERMINISTIC_REBUILD' END;
    IF v_code='exposure.binding' THEN
      v_status:='NOT_APPLICABLE'; v_reason:='profile_projection';
    ELSE
      v_status:='PASS'; v_reason:=NULL;
    END IF;
    v_item:=jsonb_build_object(
      'checkCode',v_code,'checkScope',v_scope,
      'expectedValue',CASE WHEN v_status='PASS' THEN 'exact' ELSE 'not_applicable' END,
      'observedValue',CASE WHEN v_status='PASS' THEN 'exact' ELSE 'not_applicable' END,
      'differenceValue','0','severity','INFO','checkStatus',v_status,
      'failureCode','','reasonCode',COALESCE(v_reason,''),'required',true);
    v_item:=v_item||jsonb_build_object('evidenceFingerprint',
      public.data_quality_fingerprint_v1('data-quality-check-evidence-sha256-v1',jsonb_build_object(
        'checkCode',v_item->>'checkCode','scope',v_item->>'checkScope',
        'expected',v_item->>'expectedValue','observed',v_item->>'observedValue',
        'difference',v_item->>'differenceValue','severity',v_item->>'severity',
        'status',v_item->>'checkStatus','failure',COALESCE(v_item->>'failureCode',''),
        'reason',COALESCE(v_item->>'reasonCode',''),
        'required',(v_item->>'required')::boolean)));
    v_checks:=v_checks||jsonb_build_array(v_item);
  END LOOP;

  FOR v_code IN SELECT jsonb_array_elements_text(v_policy.required_metrics) LOOP
    v_threshold:=COALESCE((v_policy.thresholds->v_code->>'value')::numeric,0);
    v_operator:=COALESCE(v_policy.thresholds->v_code->>'operator','GREATER_THAN_OR_EQUAL');
    IF v_code IN ('lineage_orphan_rate','duplicate_source_rate','duplicate_lineage_rate',
                  'late_arrival_rate','conflict_rate') THEN
      v_numerator:=0; v_value:=0;
    ELSE
      v_numerator:=1; v_value:=1;
    END IF;
    v_item:=jsonb_build_object('metricName',v_code,'numerator',v_numerator,'denominator',1,
      'metricValue',v_value::text,'metricUnit','ratio','policyThreshold',v_threshold::text,
      'thresholdOperator',v_operator,'thresholdResult','PASS','metricVersion','data-quality-metric-v1');
    v_item:=v_item||jsonb_build_object('metricFingerprint',
      public.data_quality_fingerprint_v1('data-quality-metric-sha256-v1',jsonb_build_object(
        'metricName',v_item->>'metricName','numerator',(v_item->>'numerator')::bigint,
        'denominator',(v_item->>'denominator')::bigint,'metricValue',v_item->>'metricValue',
        'metricUnit',v_item->>'metricUnit','threshold',(v_item->>'policyThreshold')::numeric,
        'operator',v_item->>'thresholdOperator',
        'thresholdResult',v_item->>'thresholdResult','metricVersion',v_item->>'metricVersion')));
    v_metrics:=v_metrics||jsonb_build_array(v_item);
  END LOOP;

  SELECT COALESCE(jsonb_agg(projection_record_fingerprint ORDER BY projection_record_fingerprint),'[]'::jsonb)
    INTO v_record_fps
  FROM public.data_recommendation_profile_input_projection_v1
  WHERE snapshot_ref=v_snapshot.snapshot_id;
  v_rebuild:=jsonb_build_object('matched',true,'expectedRecordCount',v_snapshot.record_count,
    'observedRecordCount',v_snapshot.record_count,'expectedSubjectCount',v_snapshot.subject_count,
    'observedSubjectCount',v_snapshot.subject_count,'expectedSourceCount',v_snapshot.source_event_count,
    'observedSourceCount',v_snapshot.source_event_count,'expectedRecordFingerprints',v_record_fps,
    'observedRecordFingerprints',v_record_fps,'expectedSnapshotFingerprint',v_snapshot.content_fingerprint,
    'observedSnapshotFingerprint',v_snapshot.content_fingerprint,
    'expectedLineageFingerprint',v_snapshot.lineage_fingerprint,
    'observedLineageFingerprint',v_snapshot.lineage_fingerprint);
  v_rebuild:=v_rebuild||jsonb_build_object('comparisonFingerprint',
    public.data_quality_fingerprint_v1('data-quality-rebuild-comparison-sha256-v1',v_rebuild));
  SELECT count(*) INTO v_passed FROM jsonb_array_elements(v_checks)c WHERE c->>'checkStatus'='PASS';
  v_verdict:=jsonb_build_object('overallStatus','VALIDATED','blockerCount',0,'errorCount',0,
    'warningCount',0,'passedCheckCount',v_passed,'failedCheckCount',0,
    'skippedRequiredCheckCount',0,'qualityScore','100','qualityPolicyVersion','data-quality-policy-v1');
  v_verdict:=v_verdict||jsonb_build_object('verdictFingerprint',
    public.data_quality_fingerprint_v1('data-quality-verdict-sha256-v1',v_verdict));

  SELECT disposition INTO v_disposition
  FROM public.persist_data_quality_validation_v1(p_run_ref,'FULL','snapshot:dp5-profile',
    'data-quality-validator-v1','data-quality-policy-v1','2026-07-22T00:00:00Z',v_input,
    v_checks,v_metrics,'[]'::jsonb,v_verdict,v_rebuild,'[]'::jsonb);
  RETURN v_disposition;
END;
$$;
SQL

(psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -c \
  "SELECT pg_sleep(0.5); SELECT public.dp6_concurrency_attempt('quality_run:dp6-concurrency-a');" \
  >"$work/a" 2>&1) & p1=$!
(psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -c \
  "SELECT pg_sleep(0.5); SELECT public.dp6_concurrency_attempt('quality_run:dp6-concurrency-b');" \
  >"$work/b" 2>&1) & p2=$!
wait "$p1"
wait "$p2"

cat "$work/a" "$work/b"
mapfile -t dispositions < <(cat "$work/a" "$work/b" | grep -E '^(NEW|DUPLICATE|CONFLICT)$' | sort)
if [[ "${#dispositions[@]}" -ne 2 || "${dispositions[0]}" != "DUPLICATE" || "${dispositions[1]}" != "NEW" ]]; then
  printf 'DP-6 concurrency dispositions invalid: %s\n' "${dispositions[*]:-none}" >&2
  exit 1
fi

counts="$(psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -F ':' -c "
  SELECT
    (SELECT count(*) FROM public.data_quality_validation_run_v1 r
      JOIN public.data_projection_snapshot_v1 s ON s.snapshot_id=r.snapshot_ref
      WHERE s.snapshot_ref='snapshot:dp5-profile'),
    (SELECT count(*) FROM public.data_snapshot_quality_verdict_v1 v
      JOIN public.data_projection_snapshot_v1 s ON s.snapshot_id=v.snapshot_ref
      WHERE s.snapshot_ref='snapshot:dp5-profile'),
    (SELECT count(*) FROM public.data_quality_rebuild_comparison_v1 c
      JOIN public.data_quality_validation_run_v1 r ON r.validation_run_id=c.validation_run_ref
      JOIN public.data_projection_snapshot_v1 s ON s.snapshot_id=r.snapshot_ref
      WHERE s.snapshot_ref='snapshot:dp5-profile'),
    (SELECT count(*) FROM public.data_quality_validation_conflict_evidence_v1);
")"
echo "DP-6 concurrency persisted counts: $counts"
[[ "$counts" == "1:1:1:0" ]]

psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -c \
  "DROP FUNCTION public.dp6_concurrency_attempt(varchar);" >/dev/null
echo "DP-6 concurrent same logical identity: exactly one NEW and one DUPLICATE: PASS"
