-- Journey Connect DB v2.7 extension - DP-2 event-store contract smoke test
-- Target: PostgreSQL 15 and 18
-- Run after canonical SQL 01..30. Fixture rows are rolled back.

BEGIN;

DO $$
DECLARE
  v_bytes bytea;
  v_conflict_bytes bytea;
  v_noncanonical_bytes bytea;
  v_excluded_field_bytes bytea;
  v_fingerprint varchar(64);
  v_conflict_fingerprint varchar(64);
  v_noncanonical_fingerprint varchar(64);
  v_excluded_field_fingerprint varchar(64);
  v_result record;
  v_event_count bigint;
  v_binding_count bigint;
  v_duplicate_count bigint;
  v_conflict_count bigint;
  v_denied boolean;
BEGIN
  IF to_regclass('public.data_platform_event_v1') IS NULL
     OR to_regclass('public.data_event_idempotency_binding_v1') IS NULL
     OR to_regprocedure('public.ingest_data_platform_event_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,bytea,varchar,varchar,varchar,varchar,timestamptz,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,varchar,jsonb)') IS NULL THEN
    RAISE EXCEPTION 'DP-2 event-store objects are missing.';
  END IF;

  v_bytes := convert_to(
    '{"actorRef":"subject:dp2-smoke-user","canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:123","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T00:00:00Z","payload":{"surface":"feed"},"schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-smoke-session"}',
    'UTF8');
  v_fingerprint := encode(public.digest(v_bytes, 'sha256'), 'hex');

  SELECT * INTO v_result
  FROM public.ingest_data_platform_event_v1(
    'journey-connect', 'event:dp2-smoke-new', 'platform-event-v1',
    'user-behavior-event-v1', 'platform-event-canonical-json-v1',
    'platform-event-fingerprint-sha256-v1', v_fingerprint, v_bytes,
    'jc-backend-event-producer-v1', 'git:0123456789abcdef0123456789abcdef01234567',
    'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
    '2026-07-22T00:00:00.100Z'::timestamptz, 'subject:dp2-smoke-user',
    'session:dp2-smoke-session', 'post:123', 'request:dp2-smoke-new',
    'correlation:dp2-smoke', NULL, 'dp2-smoke-key', '{"surface":"feed"}'::jsonb
  );
  IF v_result.disposition <> 'NEW' OR v_result.canonical_event_id <> 'event:dp2-smoke-new'
     OR v_result.idempotency_binding_id IS NULL OR v_result.error_code IS NOT NULL THEN
    RAISE EXCEPTION 'DP-2 NEW result invalid: %', row_to_json(v_result);
  END IF;

  SELECT * INTO v_result
  FROM public.ingest_data_platform_event_v1(
    'journey-connect', 'event:dp2-smoke-duplicate-candidate', 'platform-event-v1',
    'user-behavior-event-v1', 'platform-event-canonical-json-v1',
    'platform-event-fingerprint-sha256-v1', v_fingerprint, v_bytes,
    'jc-backend-event-producer-v1', 'git:89abcdef0123456789abcdef0123456789abcdef',
    'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
    '2026-07-22T00:00:02Z'::timestamptz, 'subject:dp2-smoke-user',
    'session:dp2-smoke-session', 'post:123', 'request:dp2-smoke-duplicate',
    'correlation:dp2-smoke-retry', NULL, 'dp2-smoke-key', '{"surface":"feed"}'::jsonb
  );
  IF v_result.disposition <> 'DUPLICATE'
     OR v_result.canonical_event_id <> 'event:dp2-smoke-new'
     OR v_result.error_code IS NOT NULL THEN
    RAISE EXCEPTION 'DP-2 DUPLICATE result invalid: %', row_to_json(v_result);
  END IF;

  v_conflict_bytes := convert_to(
    '{"actorRef":"subject:dp2-smoke-user","canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:123","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T00:00:00Z","payload":{"surface":"search"},"schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-smoke-session"}',
    'UTF8');
  v_conflict_fingerprint := encode(public.digest(v_conflict_bytes, 'sha256'), 'hex');

  SELECT * INTO v_result
  FROM public.ingest_data_platform_event_v1(
    'journey-connect', 'event:dp2-smoke-conflict-candidate', 'platform-event-v1',
    'user-behavior-event-v1', 'platform-event-canonical-json-v1',
    'platform-event-fingerprint-sha256-v1', v_conflict_fingerprint, v_conflict_bytes,
    'jc-backend-event-producer-v1', 'git:fedcba9876543210fedcba9876543210fedcba98',
    'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
    '2026-07-22T00:00:03Z'::timestamptz, 'subject:dp2-smoke-user',
    'session:dp2-smoke-session', 'post:123', 'request:dp2-smoke-conflict',
    'correlation:dp2-smoke-conflict', NULL, 'dp2-smoke-key', '{"surface":"search"}'::jsonb
  );
  IF v_result.disposition <> 'CONFLICT'
     OR v_result.canonical_event_id <> 'event:dp2-smoke-new'
     OR v_result.error_code <> 'IDEMPOTENCY_CONFLICT' THEN
    RAISE EXCEPTION 'DP-2 CONFLICT result invalid: %', row_to_json(v_result);
  END IF;

  SELECT count(*) INTO v_event_count
  FROM public.data_platform_event_v1 WHERE idempotency_key = 'dp2-smoke-key';
  SELECT count(*) INTO v_binding_count
  FROM public.data_event_idempotency_binding_v1 WHERE idempotency_key = 'dp2-smoke-key';
  SELECT count(*) INTO v_duplicate_count
  FROM public.data_event_duplicate_observation_v1
  WHERE canonical_event_id = 'event:dp2-smoke-new';
  SELECT count(*) INTO v_conflict_count
  FROM public.data_event_conflict_observation_v1
  WHERE canonical_event_id = 'event:dp2-smoke-new';

  IF v_event_count <> 1 OR v_binding_count <> 1
     OR v_duplicate_count <> 1 OR v_conflict_count <> 1 THEN
    RAISE EXCEPTION 'DP-2 evidence counts invalid: events %, bindings %, duplicates %, conflicts %',
      v_event_count, v_binding_count, v_duplicate_count, v_conflict_count;
  END IF;

  BEGIN
    PERFORM public.ingest_data_platform_event_v1(
      'journey-connect', 'event:dp2-invalid-fingerprint', 'platform-event-v1',
      'user-behavior-event-v1', 'platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1', repeat('0', 64), v_bytes,
      'jc-backend-event-producer-v1', 'git:0123456789abcdef0123456789abcdef01234567',
      'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
      '2026-07-22T00:00:01Z'::timestamptz, 'subject:dp2-smoke-user',
      'session:dp2-smoke-session', 'post:123', NULL, NULL, NULL,
      'dp2-invalid-fingerprint-key', '{"surface":"feed"}'::jsonb
    );
    RAISE EXCEPTION 'Invalid fingerprint was accepted.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  v_noncanonical_bytes := convert_to(
    '{ "actorRef" : "subject:dp2-smoke-user", "canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:123","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T00:00:00Z","payload":{"surface":"feed"},"schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-smoke-session" }',
    'UTF8');
  v_noncanonical_fingerprint := encode(public.digest(v_noncanonical_bytes, 'sha256'), 'hex');
  BEGIN
    PERFORM public.ingest_data_platform_event_v1(
      'journey-connect', 'event:dp2-noncanonical-bytes', 'platform-event-v1',
      'user-behavior-event-v1', 'platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1', v_noncanonical_fingerprint, v_noncanonical_bytes,
      'jc-backend-event-producer-v1', 'git:0123456789abcdef0123456789abcdef01234567',
      'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
      '2026-07-22T00:00:01Z'::timestamptz, 'subject:dp2-smoke-user',
      'session:dp2-smoke-session', 'post:123', NULL, NULL, NULL,
      'dp2-noncanonical-key', '{"surface":"feed"}'::jsonb
    );
    RAISE EXCEPTION 'Noncanonical fingerprint bytes were accepted.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  v_excluded_field_bytes := convert_to(
    '{"actorRef":"subject:dp2-smoke-user","canonicalizationVersion":"platform-event-canonical-json-v1","causationId":null,"contractVersion":"platform-event-v1","entityRef":"post:123","eventFamily":"user_behavior","eventType":"post_view","occurredAt":"2026-07-22T00:00:00Z","payload":{"surface":"feed"},"receivedAt":"2026-07-22T00:00:01Z","schemaVersion":"user-behavior-event-v1","sessionRef":"session:dp2-smoke-session"}',
    'UTF8');
  v_excluded_field_fingerprint := encode(public.digest(v_excluded_field_bytes, 'sha256'), 'hex');
  BEGIN
    PERFORM public.ingest_data_platform_event_v1(
      'journey-connect', 'event:dp2-excluded-field', 'platform-event-v1',
      'user-behavior-event-v1', 'platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1', v_excluded_field_fingerprint, v_excluded_field_bytes,
      'jc-backend-event-producer-v1', 'git:0123456789abcdef0123456789abcdef01234567',
      'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
      '2026-07-22T00:00:01Z'::timestamptz, 'subject:dp2-smoke-user',
      'session:dp2-smoke-session', 'post:123', NULL, NULL, NULL,
      'dp2-excluded-field-key', '{"surface":"feed"}'::jsonb
    );
    RAISE EXCEPTION 'Excluded fingerprint field was accepted.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    PERFORM public.ingest_data_platform_event_v1(
      'journey-connect', 'event:dp2-invalid-identity', 'platform-event-v1',
      'user-behavior-event-v1', 'platform-event-canonical-json-v1',
      'platform-event-fingerprint-sha256-v1', v_fingerprint, v_bytes,
      'jc-backend-event-producer-v1', 'git:0123456789abcdef0123456789abcdef01234567',
      'user_behavior', 'post_view', '2026-07-22T00:00:00Z'::timestamptz,
      '2026-07-22T00:00:01Z'::timestamptz, 'user:10', NULL,
      'post:123', NULL, NULL, NULL, 'dp2-invalid-identity-key', '{"surface":"feed"}'::jsonb
    );
    RAISE EXCEPTION 'Legacy/raw actor namespace was accepted.';
  EXCEPTION WHEN invalid_parameter_value THEN
    NULL;
  END;

  IF EXISTS (
    SELECT 1 FROM public.data_platform_event_v1
    WHERE event_id IN ('event:dp2-invalid-fingerprint', 'event:dp2-noncanonical-bytes',
      'event:dp2-excluded-field', 'event:dp2-invalid-identity')
  ) THEN
    RAISE EXCEPTION 'Rejected DP-2 input created a canonical event.';
  END IF;

  v_denied := false;
  BEGIN
    UPDATE public.data_platform_event_v1 SET received_at = received_at
    WHERE event_id = 'event:dp2-smoke-new';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN
    v_denied := true;
  END;
  IF NOT v_denied THEN RAISE EXCEPTION 'Append-only UPDATE barrier missing.'; END IF;

  v_denied := false;
  BEGIN
    DELETE FROM public.data_platform_event_v1 WHERE event_id = 'event:dp2-smoke-new';
  EXCEPTION WHEN object_not_in_prerequisite_state THEN
    v_denied := true;
  END;
  IF NOT v_denied THEN RAISE EXCEPTION 'Append-only DELETE barrier missing.'; END IF;

  IF NOT has_function_privilege('jc_data_event_writer',
      'public.ingest_data_platform_event_v1(varchar,varchar,varchar,varchar,varchar,varchar,varchar,bytea,varchar,varchar,varchar,varchar,timestamptz,timestamptz,varchar,varchar,varchar,varchar,varchar,varchar,varchar,jsonb)',
      'EXECUTE') THEN
    RAISE EXCEPTION 'Writer lacks atomic ingest execute privilege.';
  END IF;
  IF has_table_privilege('jc_data_event_writer', 'public.data_platform_event_v1', 'INSERT')
     OR has_table_privilege('jc_data_event_writer', 'public.data_platform_event_v1', 'UPDATE')
     OR has_table_privilege('jc_data_event_writer', 'public.data_platform_event_v1', 'DELETE') THEN
    RAISE EXCEPTION 'Writer has forbidden direct table mutation privilege.';
  END IF;
  IF NOT has_table_privilege('jc_data_event_reader', 'public.data_platform_event_reader_v1', 'SELECT')
     OR has_table_privilege('jc_data_event_reader', 'public.data_platform_event_v1', 'SELECT')
     OR has_table_privilege('jc_data_event_reader', 'public.data_platform_event_v1', 'INSERT')
     OR has_table_privilege('jc_data_event_reader', 'public.data_platform_event_v1', 'UPDATE')
     OR has_table_privilege('jc_data_event_reader', 'public.data_platform_event_v1', 'DELETE') THEN
    RAISE EXCEPTION 'Reader privilege boundary invalid.';
  END IF;
  IF has_schema_privilege('jc_data_event_writer', 'public', 'CREATE')
     OR has_schema_privilege('jc_data_event_reader', 'public', 'CREATE')
     OR has_schema_privilege('jc_data_replay_executor', 'public', 'CREATE') THEN
    RAISE EXCEPTION 'Data runtime role has forbidden schema CREATE privilege.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_roles
    WHERE rolname IN ('jc_data_event_writer', 'jc_data_event_reader', 'jc_data_replay_executor')
      AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls)
  ) THEN
    RAISE EXCEPTION 'Data runtime role has unsafe attributes.';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM pg_auth_members memberships
    JOIN pg_roles member_role ON member_role.oid = memberships.member
    JOIN pg_roles granted_role ON granted_role.oid = memberships.roleid
    WHERE member_role.rolname IN ('jc_data_event_writer', 'jc_data_event_reader', 'jc_data_replay_executor')
       OR granted_role.rolname IN ('jc_data_event_writer', 'jc_data_event_reader', 'jc_data_replay_executor')
  ) THEN
    RAISE EXCEPTION 'Data runtime roles must not inherit roles or be inherited.';
  END IF;

  IF has_table_privilege('jc_data_replay_executor', 'public.data_platform_event_v1', 'INSERT')
     OR has_table_privilege('jc_data_replay_executor', 'public.data_platform_event_v1', 'UPDATE')
     OR has_table_privilege('jc_data_replay_executor', 'public.data_platform_event_v1', 'DELETE') THEN
    RAISE EXCEPTION 'Replay role may mutate canonical source.';
  END IF;
  IF EXISTS (
    SELECT 1
    FROM information_schema.role_table_grants grants
    WHERE grants.grantee = 'PUBLIC'
      AND grants.table_schema = 'public'
      AND grants.table_name LIKE 'data\_%' ESCAPE E'\\'
  ) OR EXISTS (
    SELECT 1
    FROM information_schema.routine_privileges grants
    WHERE grants.grantee = 'PUBLIC'
      AND grants.specific_schema = 'public'
      AND grants.routine_name = 'ingest_data_platform_event_v1'
  ) THEN
    RAISE EXCEPTION 'PUBLIC Data access is not closed.';
  END IF;
  IF has_table_privilege('jc_data_event_writer', 'public.recommendation_behavior_event', 'INSERT')
     OR has_table_privilege('jc_data_event_writer', 'public.search_document_projection_v1', 'INSERT') THEN
    RAISE EXCEPTION 'Data writer can write another track object.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname ~ '^purge_.*data.*event'
  ) THEN
    RAISE EXCEPTION 'DP-2 must not install an automatic purge path.';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.data_platform_event_v1
    WHERE event_id = 'event:dp2-smoke-new'
      AND retention_class = 'canonical_event_365d'
      AND retention_policy_version = 'data-retention-policy-v1'
      AND expires_at = received_at + interval '365 days'
  ) OR NOT EXISTS (
    SELECT 1 FROM public.data_event_idempotency_binding_v1
    WHERE event_id = 'event:dp2-smoke-new'
      AND retention_class = 'idempotency_binding_30d'
      AND expires_at = created_at + interval '30 days'
  ) THEN
    RAISE EXCEPTION 'DP-2 retention metadata invalid.';
  END IF;

  RAISE NOTICE 'DP-2 event store smoke PASS';
END;
$$;

ROLLBACK;
