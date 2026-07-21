-- Journey Connect DB v2.7 extension - IP-11.5 Search document projection
-- Target: PostgreSQL 15+
-- Prerequisite: canonical migrations 01..26.
-- This migration adds a fail-closed, rebuildable Search read projection. It does not activate production shadow.

BEGIN;

CREATE TABLE public.search_document_operational_eligibility_v1 (
  source_post_id bigint PRIMARY KEY REFERENCES public.posts(id) ON DELETE CASCADE,
  eligibility_status varchar(20) NOT NULL
    CHECK (eligibility_status IN ('eligible', 'excluded')),
  policy_version varchar(80) NOT NULL
    CHECK (policy_version = 'search-document-operational-eligibility-v1'),
  source_version bigint NOT NULL CHECK (source_version > 0),
  reason_code varchar(64) NOT NULL
    CHECK (reason_code ~ '^[a-z][a-z0-9_]{0,63}$'),
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE public.search_document_operational_eligibility_v1 IS
  'Operations-owned eligibility authority. Missing row is fail-closed and excludes the post from Search projection.';

CREATE TABLE public.search_document_projection_v1 (
  document_id varchar(64) PRIMARY KEY,
  source_post_id bigint NOT NULL UNIQUE REFERENCES public.posts(id) ON DELETE CASCADE,
  source_version bigint NOT NULL CHECK (source_version > 0),
  projection_schema_version varchar(80) NOT NULL
    CHECK (projection_schema_version = 'search-document-projection-v1'),
  eligibility_policy_version varchar(80) NOT NULL
    CHECK (eligibility_policy_version = 'search-document-eligibility-v1'),
  operational_policy_version varchar(80) NOT NULL
    CHECK (operational_policy_version = 'search-document-operational-eligibility-v1'),
  region_id bigint NOT NULL REFERENCES public.regions(id) ON DELETE RESTRICT,
  region_reference varchar(160) NOT NULL
    CHECK (region_reference ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  place_reference varchar(64),
  normalized_title_terms text[] NOT NULL DEFAULT ARRAY[]::text[],
  normalized_body_terms text[] NOT NULL DEFAULT ARRAY[]::text[],
  visibility_status varchar(20) NOT NULL CHECK (visibility_status = 'public'),
  publication_status varchar(20) NOT NULL CHECK (publication_status = 'published'),
  moderation_eligibility varchar(20) NOT NULL CHECK (moderation_eligibility = 'eligible'),
  deletion_status varchar(20) NOT NULL CHECK (deletion_status = 'active'),
  source_updated_at timestamptz NOT NULL,
  projected_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deterministic_content_hash varchar(64) NOT NULL
    CHECK (deterministic_content_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT search_document_projection_document_id_check
    CHECK (document_id = 'post:' || source_post_id::text),
  CONSTRAINT search_document_projection_place_ref_check
    CHECK (place_reference IS NULL OR place_reference ~ '^place:[1-9][0-9]{0,18}$')
);

CREATE INDEX search_document_projection_region_idx
  ON public.search_document_projection_v1 (region_reference, source_updated_at DESC, source_post_id DESC);
CREATE INDEX search_document_projection_updated_idx
  ON public.search_document_projection_v1 (source_updated_at DESC, source_post_id DESC);
CREATE INDEX search_document_projection_title_terms_gin_idx
  ON public.search_document_projection_v1 USING gin (normalized_title_terms);
CREATE INDEX search_document_projection_body_terms_gin_idx
  ON public.search_document_projection_v1 USING gin (normalized_body_terms);

CREATE OR REPLACE FUNCTION public.search_projection_terms_v1(p_text text, p_max_terms integer)
RETURNS text[]
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_result text[];
BEGIN
  IF p_max_terms < 1 OR p_max_terms > 2048 THEN
    RAISE EXCEPTION 'p_max_terms must be 1..2048.' USING ERRCODE = '22023';
  END IF;

  SELECT COALESCE(array_agg(x.term ORDER BY x.first_ordinal), ARRAY[]::text[])
    INTO v_result
  FROM (
    SELECT token AS term, min(ordinality) AS first_ordinal
    FROM unnest(regexp_split_to_array(lower(COALESCE(p_text, '')), '[^[:alnum:]]+'))
         WITH ORDINALITY AS parts(token, ordinality)
    WHERE token <> '' AND char_length(token) <= 128
    GROUP BY token
    ORDER BY min(ordinality)
    LIMIT p_max_terms
  ) x;

  RETURN v_result;
END;
$$;

CREATE OR REPLACE FUNCTION public.project_search_document_v1(p_source_post_id bigint)
RETURNS varchar
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
  v_post record;
  v_source_version bigint;
  v_title_terms text[];
  v_body_terms text[];
  v_content_hash varchar(64);
  v_existing_version bigint;
  v_existing_hash varchar(64);
BEGIN
  IF p_source_post_id IS NULL OR p_source_post_id < 1 THEN
    RAISE EXCEPTION 'source post ID must be positive.' USING ERRCODE = '22023';
  END IF;

  SELECT p.id,
         p.updated_at,
         p.main_region_id,
         r.slug AS region_reference,
         p.title,
         p.content,
         min(pp.place_id) AS place_id,
         oe.policy_version AS operational_policy_version,
         oe.source_version AS operational_source_version
    INTO v_post
  FROM public.posts p
  JOIN public.app_users u
    ON u.id = p.author_id
   AND u.account_status = 'active'
  JOIN public.regions r
    ON r.id = p.main_region_id
   AND r.is_active = true
  JOIN public.search_document_operational_eligibility_v1 oe
    ON oe.source_post_id = p.id
   AND oe.eligibility_status = 'eligible'
   AND oe.policy_version = 'search-document-operational-eligibility-v1'
  LEFT JOIN public.post_places pp ON pp.post_id = p.id
  WHERE p.id = p_source_post_id
    AND p.visibility = 'public'
    AND p.status = 'published'
    AND p.deleted_at IS NULL
    AND p.moderation_status = 'visible'
  GROUP BY p.id, p.updated_at, p.main_region_id, r.slug, p.title, p.content,
           oe.policy_version, oe.source_version;

  IF NOT FOUND OR v_post.place_id IS NULL THEN
    DELETE FROM public.search_document_projection_v1
    WHERE source_post_id = p_source_post_id;
    RETURN 'removed_ineligible_or_missing';
  END IF;

  v_source_version := GREATEST(
    1,
    floor(extract(epoch FROM v_post.updated_at) * 1000000)::bigint,
    v_post.operational_source_version
  );
  v_title_terms := public.search_projection_terms_v1(v_post.title, 128);
  v_body_terms := public.search_projection_terms_v1(v_post.content, 1024);
  v_content_hash := encode(public.digest(convert_to(concat_ws(E'\n',
    'search-document-projection-v1',
    v_post.id::text,
    v_source_version::text,
    v_post.main_region_id::text,
    v_post.region_reference,
    'place:' || v_post.place_id::text,
    array_to_string(v_title_terms, '|'),
    array_to_string(v_body_terms, '|'),
    v_post.updated_at::text,
    v_post.operational_policy_version
  ), 'UTF8'), 'sha256'), 'hex');

  SELECT source_version, deterministic_content_hash
    INTO v_existing_version, v_existing_hash
  FROM public.search_document_projection_v1
  WHERE source_post_id = p_source_post_id;

  IF FOUND THEN
    IF v_existing_version > v_source_version THEN
      RETURN 'stale_ignored';
    ELSIF v_existing_version = v_source_version AND v_existing_hash = v_content_hash THEN
      RETURN 'unchanged';
    ELSIF v_existing_version = v_source_version AND v_existing_hash <> v_content_hash THEN
      RETURN 'hash_mismatch_rejected';
    END IF;
  END IF;

  INSERT INTO public.search_document_projection_v1 (
    document_id, source_post_id, source_version, projection_schema_version,
    eligibility_policy_version, operational_policy_version, region_id,
    region_reference, place_reference, normalized_title_terms, normalized_body_terms,
    visibility_status, publication_status, moderation_eligibility, deletion_status,
    source_updated_at, projected_at, deterministic_content_hash
  ) VALUES (
    'post:' || v_post.id::text, v_post.id, v_source_version, 'search-document-projection-v1',
    'search-document-eligibility-v1', v_post.operational_policy_version, v_post.main_region_id,
    v_post.region_reference, 'place:' || v_post.place_id::text, v_title_terms, v_body_terms,
    'public', 'published', 'eligible', 'active', v_post.updated_at, CURRENT_TIMESTAMP, v_content_hash
  )
  ON CONFLICT (source_post_id) DO UPDATE SET
    document_id = EXCLUDED.document_id,
    source_version = EXCLUDED.source_version,
    projection_schema_version = EXCLUDED.projection_schema_version,
    eligibility_policy_version = EXCLUDED.eligibility_policy_version,
    operational_policy_version = EXCLUDED.operational_policy_version,
    region_id = EXCLUDED.region_id,
    region_reference = EXCLUDED.region_reference,
    place_reference = EXCLUDED.place_reference,
    normalized_title_terms = EXCLUDED.normalized_title_terms,
    normalized_body_terms = EXCLUDED.normalized_body_terms,
    visibility_status = EXCLUDED.visibility_status,
    publication_status = EXCLUDED.publication_status,
    moderation_eligibility = EXCLUDED.moderation_eligibility,
    deletion_status = EXCLUDED.deletion_status,
    source_updated_at = EXCLUDED.source_updated_at,
    projected_at = EXCLUDED.projected_at,
    deterministic_content_hash = EXCLUDED.deterministic_content_hash;

  RETURN CASE WHEN v_existing_version IS NULL THEN 'inserted' ELSE 'updated' END;
END;
$$;

CREATE OR REPLACE FUNCTION public.rebuild_search_document_projection_v1()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
  v_post_id bigint;
  v_processed bigint := 0;
  v_removed bigint := 0;
BEGIN
  FOR v_post_id IN SELECT id FROM public.posts ORDER BY id LOOP
    PERFORM public.project_search_document_v1(v_post_id);
    v_processed := v_processed + 1;
  END LOOP;

  WITH removed AS (
    DELETE FROM public.search_document_projection_v1 d
    WHERE NOT EXISTS (SELECT 1 FROM public.posts p WHERE p.id = d.source_post_id)
    RETURNING 1
  ) SELECT count(*) INTO v_removed FROM removed;

  RETURN jsonb_build_object(
    'processed', v_processed,
    'orphan_removed', v_removed,
    'remaining', (SELECT count(*) FROM public.search_document_projection_v1),
    'projection_schema_version', 'search-document-projection-v1',
    'eligibility_policy_version', 'search-document-eligibility-v1'
  );
END;
$$;

REVOKE ALL ON TABLE public.search_document_operational_eligibility_v1 FROM PUBLIC;
REVOKE ALL ON TABLE public.search_document_projection_v1 FROM PUBLIC;
REVOKE ALL ON FUNCTION public.search_projection_terms_v1(text, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.project_search_document_v1(bigint) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rebuild_search_document_projection_v1() FROM PUBLIC;

GRANT SELECT ON TABLE public.search_document_projection_v1 TO jc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.search_document_operational_eligibility_v1 TO jc_security_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.search_document_projection_v1 TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.search_projection_terms_v1(text, integer) TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.project_search_document_v1(bigint) TO jc_security_owner;
GRANT EXECUTE ON FUNCTION public.rebuild_search_document_projection_v1() TO jc_security_owner;

COMMIT;
