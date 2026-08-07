\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

BEGIN;

SELECT set_config('jc.sr6fh_environment', :'sr6fh_environment', true);
SELECT set_config('jc.sr6fh_approval_ref', :'sr6fh_approval_ref', true);
SELECT set_config('jc.sr6fh_window_start', :'sr6fh_window_start', true);
SELECT set_config('jc.sr6fh_producer_build_id', :'sr6fh_producer_build_id', true);

DO $sr6fh$
DECLARE
  v_count integer;
  v_invalid integer;
BEGIN
  SELECT count(*) INTO v_count
  FROM public.search_ctr_manual_run_audit_v1
  WHERE environment = current_setting('jc.sr6fh_environment')
    AND window_start = current_setting('jc.sr6fh_window_start')::timestamptz
    AND producer_build_id = current_setting('jc.sr6fh_producer_build_id');
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'SR-6F-H requires exactly one matching manual audit row, found %', v_count
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO v_invalid
  FROM public.search_ctr_manual_run_audit_v1
  WHERE environment = current_setting('jc.sr6fh_environment')
    AND window_start = current_setting('jc.sr6fh_window_start')::timestamptz
    AND producer_build_id = current_setting('jc.sr6fh_producer_build_id')
    AND (write_status NOT IN ('STORED', 'DUPLICATE')
      OR finality_write_attempted
      OR projection_id IS NULL
      OR projection_fingerprint IS NULL
      OR projection_status IS DISTINCT FROM 'PROVISIONAL');
  IF v_invalid <> 0 THEN
    RAISE EXCEPTION 'SR-6F-H audit row violates the approved success/finality contract'
      USING ERRCODE = '23514';
  END IF;
END;
$sr6fh$;

SELECT pg_catalog.jsonb_build_object(
  'contractVersion', 'search-ctr-stage-one-shot-evidence-v1',
  'environment', audit.environment,
  'approvalRef', current_setting('jc.sr6fh_approval_ref'),
  'operationId', audit.operation_id,
  'writeStatus', audit.write_status,
  'windowStart', to_char(audit.window_start AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
  'windowEnd', to_char(audit.window_end AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
  'producerBuildId', audit.producer_build_id,
  'projectionId', audit.projection_id,
  'projectionFingerprint', audit.projection_fingerprint,
  'predecessorProjectionId', audit.expected_predecessor_projection_id,
  'projectionStatus', audit.projection_status,
  'eligibleExposureCount', projection.eligible_exposure_count,
  'attributedExposureCount', projection.attributed_exposure_count,
  'ctrBasisPoints', projection.ctr_basis_points,
  'sourceMaxReceivedAt', CASE
    WHEN audit.source_max_received_at IS NULL THEN NULL
    ELSE to_char(audit.source_max_received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
  END,
  'finalityWriteAttempted', audit.finality_write_attempted
)::text
FROM public.search_ctr_manual_run_audit_v1 audit
JOIN public.search_ctr_projection_snapshot_v1 projection
  ON projection.projection_id = audit.projection_id
WHERE audit.environment = current_setting('jc.sr6fh_environment')
  AND audit.window_start = current_setting('jc.sr6fh_window_start')::timestamptz
  AND audit.producer_build_id = current_setting('jc.sr6fh_producer_build_id');

ROLLBACK;
