-- IP-11.5 Search document projection smoke test
-- Run after 27_search_document_projection.sql. All fixture changes are rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_region_id bigint;
  v_place_id bigint;
  v_post_id bigint;
  v_status varchar;
  v_hash varchar(64);
BEGIN
  IF to_regclass('public.search_document_projection_v1') IS NULL THEN
    RAISE EXCEPTION 'projection table missing';
  END IF;
  IF to_regprocedure('public.project_search_document_v1(bigint)') IS NULL THEN
    RAISE EXCEPTION 'projection function missing';
  END IF;

  INSERT INTO public.app_users(email, password_hash, username, display_name)
  VALUES ('ip115-projection@example.test', 'test-hash', 'ip115_projection', 'IP115 Projection')
  RETURNING id INTO v_user_id;

  INSERT INTO public.regions(name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES ('IP115', 'IP115', 'IP115', 'ip115-test-country', 'country', 'XZ', 'UTC')
  RETURNING id INTO v_region_id;

  INSERT INTO public.places(region_id, name_local)
  VALUES (v_region_id, 'IP115 Place')
  RETURNING id INTO v_place_id;

  INSERT INTO public.posts(author_id, main_region_id, title, content, visibility, status)
  VALUES (v_user_id, v_region_id, 'Projection Title', 'Projection body content', 'public', 'draft')
  RETURNING id INTO v_post_id;

  INSERT INTO public.post_places(post_id, place_id, sort_order)
  VALUES (v_post_id, v_place_id, 0);

  INSERT INTO public.search_document_operational_eligibility_v1(
    source_post_id, eligibility_status, policy_version, source_version, reason_code)
  VALUES (v_post_id, 'eligible', 'search-document-operational-eligibility-v1', 1, 'internal_fixture');

  ALTER TABLE public.posts DISABLE TRIGGER posts_set_updated_at;

  UPDATE public.posts SET status = 'published', updated_at = clock_timestamp() WHERE id = v_post_id;
  SET CONSTRAINTS ALL IMMEDIATE;

  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'inserted' THEN RAISE EXCEPTION 'expected inserted, got %', v_status; END IF;

  SELECT deterministic_content_hash INTO v_hash
  FROM public.search_document_projection_v1 WHERE source_post_id = v_post_id;
  IF v_hash IS NULL OR v_hash !~ '^[0-9a-f]{64}$' THEN RAISE EXCEPTION 'invalid projection hash'; END IF;

  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'unchanged' THEN RAISE EXCEPTION 'expected unchanged, got %', v_status; END IF;

  UPDATE public.posts SET title = 'Projection Updated', updated_at = clock_timestamp() WHERE id = v_post_id;
  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'updated' THEN RAISE EXCEPTION 'expected updated, got %', v_status; END IF;

  UPDATE public.posts SET visibility = 'private', updated_at = clock_timestamp() WHERE id = v_post_id;
  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'removed_ineligible_or_missing' THEN RAISE EXCEPTION 'private transition not removed'; END IF;
  IF EXISTS (SELECT 1 FROM public.search_document_projection_v1 WHERE source_post_id = v_post_id) THEN RAISE EXCEPTION 'private projection remains'; END IF;

  UPDATE public.posts SET visibility = 'public', moderation_status = 'hidden', updated_at = clock_timestamp() WHERE id = v_post_id;
  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'removed_ineligible_or_missing' THEN RAISE EXCEPTION 'moderation block not removed'; END IF;

  UPDATE public.posts SET moderation_status = 'visible', updated_at = clock_timestamp() WHERE id = v_post_id;
  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'inserted' THEN RAISE EXCEPTION 'visible transition not inserted'; END IF;

  UPDATE public.posts SET status = 'deleted', updated_at = clock_timestamp() WHERE id = v_post_id;
  v_status := public.project_search_document_v1(v_post_id);
  IF v_status <> 'removed_ineligible_or_missing' THEN RAISE EXCEPTION 'delete transition not removed'; END IF;

  ALTER TABLE public.posts ENABLE TRIGGER posts_set_updated_at;

  RAISE NOTICE 'IP-11.5 projection smoke PASS';
END;
$$;

ROLLBACK;
