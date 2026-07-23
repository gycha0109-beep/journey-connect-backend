-- Journey Connect DB v2.7 extension - DP-7 atomic persistence, roles and aggregate-safe view
-- Target: PostgreSQL 15 and 18. Prerequisite: SQL 01..50.
-- Validation evidence only. No Recommendation write, Intelligence runtime, Search indexing or traffic authority.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='jc_data_integration_writer') THEN
    CREATE ROLE jc_data_integration_writer NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='jc_data_integration_reader') THEN
    CREATE ROLE jc_data_integration_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='jc_data_integration_function_owner') THEN
    CREATE ROLE jc_data_integration_function_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE v_unsafe text;
BEGIN
  SELECT string_agg(rolname,', ' ORDER BY rolname) INTO v_unsafe
  FROM pg_roles
  WHERE rolname IN ('jc_data_integration_writer','jc_data_integration_reader','jc_data_integration_function_owner')
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);
  IF v_unsafe IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe DP-7 role attributes: %',v_unsafe;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.data_cross_track_mapping_fingerprint_v1(p_mapping jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp
AS $$
  SELECT public.data_integration_fingerprint_v1('integration-mapping-sha256-v1',jsonb_build_object(
    'sourceContract',p_mapping->>'sourceContract',
    'sourceSchemaVersion',p_mapping->>'sourceSchemaVersion',
    'targetContract',p_mapping->>'targetContract',
    'targetSchemaVersion',p_mapping->>'targetSchemaVersion',
    'mappingPolicyVersion',p_mapping->>'mappingPolicyVersion',
    'targetContractPresent',COALESCE((p_mapping->>'targetContractPresent')::boolean,false),
    'targetAuthorityConfirmed',COALESCE((p_mapping->>'targetAuthorityConfirmed')::boolean,false),
    'schemaSupported',COALESCE((p_mapping->>'schemaSupported')::boolean,false),
    'requiredFieldsPresent',COALESCE((p_mapping->>'requiredFieldsPresent')::boolean,false),
    'semanticsCompatible',COALESCE((p_mapping->>'semanticsCompatible')::boolean,false),
    'unitsCompatible',COALESCE((p_mapping->>'unitsCompatible')::boolean,false),
    'domainMappingApproved',COALESCE((p_mapping->>'domainMappingApproved')::boolean,false),
    'missingRequiredFields',COALESCE((SELECT jsonb_agg(value ORDER BY value::text)
      FROM jsonb_array_elements(COALESCE(p_mapping->'missingRequiredFields','[]'::jsonb))),'[]'::jsonb),
    'semanticMappings',COALESCE(p_mapping->'semanticMappings','{}'::jsonb),
    'unitMappings',COALESCE(p_mapping->'unitMappings','{}'::jsonb)));
$$;

CREATE OR REPLACE FUNCTION public.data_cross_track_check_fingerprint_v1(p_check jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp
AS $$
  SELECT public.data_integration_fingerprint_v1('integration-check-evidence-sha256-v1',jsonb_build_object(
    'order',(p_check->>'order')::integer,
    'checkCode',p_check->>'checkCode',
    'scope',p_check->>'checkScope',
    'sourceReference',COALESCE(p_check->>'sourceReference',''),
    'targetReference',COALESCE(p_check->>'targetReference',''),
    'expected',COALESCE(p_check->>'expectedValue',''),
    'observed',COALESCE(p_check->>'observedValue',''),
    'severity',p_check->>'severity',
    'status',p_check->>'checkStatus',
    'failure',COALESCE(p_check->>'failureCode',''),
    'required',COALESCE((p_check->>'required')::boolean,false),
    'conditional',COALESCE((p_check->>'conditionalRequirement')::boolean,false)));
$$;

CREATE OR REPLACE FUNCTION public.data_cross_track_contract_matrix_fingerprint_v1(p_mapping_fingerprint varchar)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp
AS $$
  SELECT public.data_integration_fingerprint_v1('cross-track-contract-matrix-sha256-v1',
    jsonb_build_object('mappingFingerprints',jsonb_build_array(p_mapping_fingerprint)));
$$;

CREATE OR REPLACE FUNCTION public.data_cross_track_verdict_fingerprint_v1(
  p_status varchar,p_checks jsonb)
RETURNS varchar LANGUAGE sql IMMUTABLE STRICT SECURITY INVOKER
SET search_path=pg_catalog,public,pg_temp
AS $$
  WITH ordered AS (
    SELECT value c FROM jsonb_array_elements(p_checks)
    ORDER BY (value->>'order')::integer,value->>'checkCode'
  ), counts AS (
    SELECT
      count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='BLOCKER') AS blockers,
      count(*) FILTER(WHERE c->>'checkStatus'='FAIL' AND c->>'severity'='ERROR') AS errors,
      count(*) FILTER(WHERE c->>'severity'='WARNING') AS warnings,
      count(*) FILTER(WHERE c->>'checkStatus'='PASS') AS passed,
      count(*) FILTER(WHERE c->>'checkStatus'='FAIL') AS failed,
      count(*) FILTER(WHERE c->>'checkStatus'='SKIPPED' AND COALESCE((c->>'required')::boolean,false)) AS skipped,
      count(*) FILTER(WHERE COALESCE((c->>'conditionalRequirement')::boolean,false)) AS conditional,
      COALESCE(jsonb_agg(c->>'evidenceFingerprint' ORDER BY (c->>'order')::integer,c->>'checkCode'),'[]'::jsonb) AS fps
    FROM ordered
  )
  SELECT public.data_integration_fingerprint_v1('integration-verdict-sha256-v1',jsonb_build_object(
    'status',p_status,'blockerCount',blockers,'errorCount',errors,'warningCount',warnings,
    'passedCheckCount',passed,'failedCheckCount',failed,'skippedRequiredCheckCount',skipped,
    'conditionalRequirementCount',conditional,'checkFingerprints',fps)) FROM counts;
$$;

CREATE OR REPLACE FUNCTION public.persist_data_cross_track_integration_v1(p_request jsonb)
RETURNS TABLE(
  disposition varchar,
  integration_run_id uuid,
  integration_verdict_id uuid,
  overall_status varchar,
  error_code varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,public,pg_temp
AS $$
DECLARE
  v_now timestamptz:=clock_timestamp();
  v_snapshot public.data_projection_snapshot_v1%ROWTYPE;
  v_quality public.data_snapshot_quality_verdict_v1%ROWTYPE;
  v_existing public.data_cross_track_integration_run_v1%ROWTYPE;
  v_existing_verdict public.data_cross_track_integration_verdict_v1%ROWTYPE;
  v_run_id uuid;
  v_verdict_id uuid;
  v_mapping jsonb:=p_request->'mapping';
  v_identity jsonb:=p_request->'identityBinding';
  v_privacy jsonb:=p_request->'privacy';
  v_retention jsonb:=p_request->'retention';
  v_checks jsonb:=p_request->'checks';
  v_authorities jsonb:=p_request->'authorities';
  v_item jsonb;
  v_mapping_fp varchar(64);
  v_matrix_fp varchar(64);
  v_input_fp varchar(64);
  v_logical_hash varchar(64);
  v_verdict_fp varchar(64);
  v_conflict_fp varchar(64);
  v_status varchar(32);
  v_blockers bigint;
  v_errors bigint;
  v_warnings bigint;
  v_passed bigint;
  v_failed bigint;
  v_skipped bigint;
  v_conditional bigint;
  v_authority_results jsonb;
  v_required_prefix text;
  v_terminal_failure varchar(96);
BEGIN
  IF p_request IS NULL OR jsonb_typeof(p_request)<>'object'
     OR v_mapping IS NULL OR jsonb_typeof(v_mapping)<>'object'
     OR v_identity IS NULL OR jsonb_typeof(v_identity)<>'object'
     OR v_privacy IS NULL OR jsonb_typeof(v_privacy)<>'object'
     OR v_retention IS NULL OR jsonb_typeof(v_retention)<>'object'
