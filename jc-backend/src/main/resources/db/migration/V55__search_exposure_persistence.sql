CREATE TABLE public.platform_identity_mapping_v1 (
  mapping_id varchar(128) PRIMARY KEY CHECK (mapping_id ~ '^identity-map:[0-9a-f]{32}$'),
  user_id bigint NOT NULL UNIQUE REFERENCES public.app_users(id) ON DELETE RESTRICT,
  subject_ref varchar(128) NOT NULL UNIQUE CHECK (subject_ref ~ '^subject:[A-Za-z0-9][A-Za-z0-9._:-]{0,119}$'),
  identity_scheme varchar(64) NOT NULL CHECK (identity_scheme = 'platform_subject_v1'),
  mapping_version varchar(64) NOT NULL CHECK (mapping_version = 'identity-mapping-v1'),
  effective_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.platform_identity_mapping_invalidation_v1 (
  invalidation_id varchar(128) PRIMARY KEY CHECK (invalidation_id ~ '^identity-invalidation:[0-9a-f]{32}$'),
  mapping_id varchar(128) NOT NULL UNIQUE REFERENCES public.platform_identity_mapping_v1(mapping_id) ON DELETE RESTRICT,
  reason_code varchar(128) NOT NULL CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,127}$'),
  requester varchar(64) NOT NULL CHECK (requester = 'system-coordination'),
  invalidated_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.platform_identity_mapping_access_audit_v1 (
  audit_id varchar(128) PRIMARY KEY CHECK (audit_id ~ '^identity-audit:[0-9a-f]{32}$'),
  mapping_id varchar(128) NOT NULL REFERENCES public.platform_identity_mapping_v1(mapping_id) ON DELETE RESTRICT,
  purpose varchar(64) NOT NULL CHECK (purpose = 'search-exposure-write'),
  requester varchar(64) NOT NULL CHECK (requester = 'intelligence-search'),
  accessed_at timestamptz NOT NULL,
  retention_until timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (retention_until = accessed_at + interval '30 days')
);
CREATE INDEX platform_identity_mapping_audit_retention_idx
  ON public.platform_identity_mapping_access_audit_v1(retention_until);

CREATE OR REPLACE FUNCTION public.deny_platform_identity_mutation_v1() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
  RAISE EXCEPTION 'platform identity evidence is append-only' USING ERRCODE = '55000';
END $$;
CREATE OR REPLACE FUNCTION public.guard_identity_audit_mutation_v1() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
  IF TG_OP = 'DELETE' AND current_user = 'jc_security_owner'
     AND current_setting('jc.controlled_retention_purge', true) = 'identity-mapping-audit-retention-v1'
  THEN RETURN OLD; END IF;
  RAISE EXCEPTION 'identity audit is append-only' USING ERRCODE = '55000';
END $$;
CREATE TRIGGER platform_identity_mapping_append_only BEFORE UPDATE OR DELETE
  ON public.platform_identity_mapping_v1 FOR EACH ROW EXECUTE FUNCTION public.deny_platform_identity_mutation_v1();
CREATE TRIGGGER platform_identity_invalidation_append_only BEFORE UPDATE OR DELETE
  ON public.platform_identity_mapping_invalidation_v1 FOR EACH ROW EXECUTE FUNCTION public.deny_platform_identity_mutation_v1();
CREATE TRIGGER platform_identity_audit_append_only BEFORE UPDATE OR DELETE
  ON public.platform_identity_mapping_access_audit_v1 FOR EACH ROW EXECUTE FUNCTION public.guard_identity_audit_mutation_v1();

CREATE OR REPLACE FUNCTION public.resolve_platform_subject_v1(
  p_user_id bigint, p_proposed_subject_ref varchar, p_purpose varchar, p_requester varchar)
RETURNS TABLE(subject_ref varchar, identity_scheme varchar, mapping_version varchar)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_mapping public.platform_identity_mapping_v1%ROWTYPE;
BEGIN
  IF p_user_id IS NULL OR p_user_id < 1 THEN RAISE EXCEPTION 'invalid user' USING ERRCODE='22023'; END IF;
  IF p_purpose <> 'search-exposure-write' OR p_requester <> 'intelligence-search'
    THEN RAISE EXCEPTION 'identity purpose denied' USING ERRCODE='42501'; END IF;
  IF p_proposed_subject_ref IS NULL OR p_proposed_subject_ref !~ '^subject:[A-Za-z0-9][A-Za-z0-9._:-]{0,119}$'
    THEN RAISE EXCEPTION 'invalid subject' USING ERRCODE='22023'; END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('platform_identity_mapping_v1:user:'||p_user_id,0));
  PERFORM 1 FROM public.app_users WHERE id=p_user_id AND account_status='active';
  IF NOT FOUND THEN RAISE EXCEPTION 'active user missing' USING ERRCODE='23514'; END IF;
  SELECT * INTO v_mapping FROM public.platform_identity_mapping_v1 WHERE user_id=p_user_id;
  IF NOT FOUND THEN
    INSERT INTO public.platform_identity_mapping_v1
      VALUES ('identity-map:'||md5(p_proposed_subject_ref),p_user_id,p_proposed_subject_ref,
              'platform_subject_v1','identity-mapping-v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
      RETURNING * INTO v_mapping;
  END IF;
  IF EXISTS (SELECT 1 FROM public.platform_identity_mapping_invalidation_v1 WHERE mapping_id=v_mapping.mapping_id)
    THEN RAISE EXCEPTION 'identity invalidated' USING ERRCODE='23514'; END IF;
  INSERT INTO public.platform_identity_mapping_access_audit_v1
    VALUES ('identity-audit:'||md5(v_mapping.mapping_id||clock_timestamp()::text||random()::text),
            v_mapping.mapping_id,p_purpose,p_requester,CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP+interval '30 days',CURRENT_TIMESTAMP);
  RETURN QUERYSELECT v_mapping.subject_ref,v_mapping.identity_scheme,v_mapping.mapping_version;
END $$;

CREATE OR REPLACE FUNCTION public.invalidate_platform_subject_v1(
  p_subject_ref varchar, p_reason_code varchar, p_requester varchar) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE v_mapping_id varchar(128);
BEGIN
  IF p_requester <> 'system-coordination' THEN RAISE EXCEPTION 'requester denied' USING ERRCODE='42501'; END IF;
  IF p_reason_code IS NULL OR p_reason_code !~ '^[A-Z][A-Z0-9_]{0,127}$'
    THEN RAISE EXCEPTION 'invalid reason' USING ERRCODE='22023'; END IF;
  SELECT mapping_id INTO v_mapping_id FROM public.platform_identity_mapping_v1 WHERE subject_ref=p_subject_ref;
  IF NOT FOUND THEN RAISE EXCEPTION 'mapping missing' USING ERRCODE='P0002'; END IF;
  INSERT INTO public.platform_identity_mapping_invalidation_v1
    VALUES ('identity-invalidation:'||md5(v_mapping_id||clock_timestamp()::text),v_mapping_id,
            p_reason_code,p_requester,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
    ON CONFLICT (mapping_id) DO NOTHING;
END $$;

CREATE TABLE public.search_exposure_event_v1 (
  exposure_id varchar(128) PRIMARY KEY CHECK (exposure_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
  idempotency_key varchar(160) NOT NULL UNIQUE CHECK (idempotency_key ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$'),
  schema_version varchar(64) NOT NULL CHECK (schema_version='search-exposure-v1'),
  payload_fingerprint varchar(64) NOT NULL CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
  canonical_payload bytea NOT NULL,
  payload_size_bytes integer NOT NULL CHECK (payload_size_bytes BETWEEN 1 AND 262144),
  batch_fingerprint varchar(64) NOT NULL CHECK (batch_fingerprint ~ '^[0-9a-f]{64}$'),
  search_run_id varchar(128) NOT NULL,
  result_snapshot_ref varchar(64) NOT NULL CHECK (result_snapshot_ref ~ '^[0-9a-f]{64}$'),
  subject_ref varchar(128) NOT NULL REFERENCES public.platform_identity_mapping_v1(subject_ref) ON DELETE RESTRICT,
  identity_scheme varchar(64) NOT NULL CHECK (identity_scheme='platform_subject_v1'),
  identity_mapping_version varchar(64) NOT NULL CHECK (identity_mapping_version='identity-mapping-v1'),
  session_id varchar(128) NOT NULL,
  surface varchar(16) NOT NULL CHECK (surface='search'),
  query_fingerprint varchar(64) NOT NULL CHECK (query_fingerprint ~ '^[0-9a-f]{64}$'),
  ranking_policy_version varchar(128) NOT NULL,
  page_occurrence_id varchar(128) NOT NULL,
  result_entity_type varchar(16) NOT NULL CHECK (result_entity_type='post'),
  result_entity_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE RESTRICT,
  absolute_rank integer NOT NULL CHECK (absolute_rank>0),
  page_position integer NOT NULL CHECK (page_position BETWEEN 1 AND 100),
  visibility_rule_version varchar(128) NOT NULL CHECK (visibility_rule_version='search-item-visible-v1'),
  visible_ratio_basis_points integer NOT NULL CHECK (visible_ratio_basis_points BETWEEN 5000 AND 10000),
  dwell_milliseconds bigint NOT NULL CHECK (dwell_milliseconds BETWEEN 1000 AND 86400000),
  exposed_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retention_policy_version varchar(128) NOT NULL CHECK (retention_policy_version='search-exposure-retention-v1'),
  retention_until timestamptz NOT NULL,
  producer_build_id varchar(128) NOT NULL,
  CHECK (octet_length(canonical_payload)=payload_size_bytes),
  CHECK (payload_fingerprint=encode(public.digest(canonical_payload,'sha256'),'hex')),
  CHECK (retention_until=exposed_at+interval '180 days'),
  CHECK (received_at>=exposed_at-interval '5 minutes'),
  UNIQUE(subject_ref,session_id,search_run_id,page_occurrence_id,result_entity_id,absolute_rank,visibility_rule_version)
);
CREATE INDEX search_exposure_run_time_idx ON public.search_exposure_event_v1(search_run_id,exposed_at);
CREATE INDEX search_exposure_subject_time_idx ON public.search_exposure_event_v1(subject_ref,exposed_at DESC);
CREATE INDEX search_exposure_retention_idx ON public.search_exposure_event_v1(retention_until);

CREATE OR REPLACE FUNCTION public.guard_search_exposure_mutation_v1() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN
  IF TG_OP='DELETE' AND current_user='jc_security_owner'
     AND current_setting('jc.controlled_retention_purge',true)='search-exposure-retention-v1'
  THEN RETURN OLD; END IF;
  RAISE EXCEPTION 'search exposure is append-only' USING ERRCODE='55000';
END $$;
CREATE TRIGGGER search_exposure_append_only BEFORE UPDATE OR DELETE ON public.search_exposure_event_v1
  FOR EACD ROW EXECUTE FUNCTION public.guard_search_exposure_mutation_v1();

CREATE OR REPLACE FUNCTION public.purge_expired_search_exposure_v1(p_cutoff timestamptz,p_requester varchar)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE v_deleted bigint; BEGIN
  IF p_requester<>'system-coordination' THEN RAISE EXCEPTION 'requester denied' USING ERRCODE='42501'; END IF;
  IF p_cutoff IS NULL OR p_cutoff>CURRENT_TIMESTAMP THEN RAISE EXCEPTION 'invalid cutoff' USING ERRCODE='22023'; END IF;
  PERFORM set_config('jc.controlled_retention_purge','identity-mapping-audit-retention-v1',true);
  DELETE FROM public.platform_identity_mapping_access_audit_v1 WHERE retention_until<=p_cutoff;
  GET DIAGNOSTICS v_deleted=ROW_COUNT; RETURN v_deleted;
END $$;

REVOKE ALL ON public.platform_identity_mapping_v1,public.platform_identity_mapping_invalidation_v1,
  public.platform_identity_mapping_access_audit_v1,public.search_exposure_event_v1
FROM PUBLIC,jc_app,jc_auth,jc_admin,jc_recommendation;
GRANT SELECT(id,account_status) ON public.app_users TO jc_security_owner;
GRANT SELECT,INSERT ON public.platform_identity_mapping_v1,public.platform_identity_mapping_invalidation_v1 TO jc_security_owner;
GRANT SELECT,INSERT,DELETE ON public.platform_identity_mapping_access_audit_v1 TO jc_security_owner;
GRANT SELECT,DELETE ON public.search_exposure_event_v1 TO jc_security_owner;
ALTER FUNCTION public.resolve_platform_subject_v1(bigint,varchar,varchar,varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.invalidate_platform_subject_v1(varchar,varchar,varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.purge_expired_search_exposure_v1(timestamptz,varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.purge_expired_identity_mapping_audit_v1(timestamptz,varchar) OWNER TO jc_security_owner;
REVOKE ALL ON FUNCTION public.resolve_platform_subject_v1(bigint,varchar,varchar,varchar),
  public.invalidate_platform_subject_v1(varchar,varchar,varchar),
  public.purge_expired_search_exposure_v1(timestamptz,varchar),
  public.purge_expired_identity_mapping_audit_v1(timestamptz,varchar) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_platform_subject_v1(bigint,varchar,varchar,varchar) TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.invalidate_platform_subject_v1(varchar,varchar,varchar),
  public.purge_expired_search_exposure_v1(timestamptz,varchar),
  public.purge_expired_identity_mapping_audit_v1(timestamptz,varchar) TO jc_security_owner;
GRANT SELECT,INSERT ON public.search_exposure_event_v1 TO jc_recommendation;
REVOKE UPDATE,DELETE,TRUNCATE ON public.search_exposure_event_v1 FROM jc_recommendation;
