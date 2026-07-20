-- Journey Connect DB v1.8 - Base schema smoke test
-- Run after 01_initial_schema.sql. Test data is rolled back.

BEGIN;

DO $$
DECLARE
  v_user_id bigint;
  v_country_id bigint;
  v_city_id bigint;
  v_area_id bigint;
  v_other_country_id bigint;
  v_other_city_id bigint;
  v_place_id bigint;
  v_other_place_id bigint;
  v_post_id bigint;
  v_bad_post_id bigint;
  v_count bigint;
BEGIN
  INSERT INTO public.app_users (email, password_hash, username, display_name)
  VALUES ('schema-smoke@example.com', '$2a$10$schemaSmokeTestHashPlaceholder', 'schema_smoke', '스키마 테스트')
  RETURNING id INTO v_user_id;

  INSERT INTO public.regions (name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES ('Smoke Country', '테스트 국가', 'Smoke Country', 'smoke-country', 'country', 'ZZ', 'UTC')
  RETURNING id INTO v_country_id;

  INSERT INTO public.regions (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES (v_country_id, 'Smoke City', '테스트 도시', 'Smoke City', 'smoke-country-city', 'city', 'ZZ', 'UTC')
  RETURNING id INTO v_city_id;

  INSERT INTO public.regions (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES (v_city_id, 'Smoke Area', '테스트 지역', 'Smoke Area', 'smoke-country-city-area', 'neighborhood', 'ZZ', 'UTC')
  RETURNING id INTO v_area_id;

  INSERT INTO public.regions (name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES ('Other Country', '다른 국가', 'Other Country', 'other-country', 'country', 'YY', 'UTC')
  RETURNING id INTO v_other_country_id;

  INSERT INTO public.regions (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone)
  VALUES (v_other_country_id, 'Other City', '다른 도시', 'Other City', 'other-country-city', 'city', 'YY', 'UTC')
  RETURNING id INTO v_other_city_id;

  BEGIN
    INSERT INTO public.regions
      (name_local, name_ko, name_en, slug, region_type, country_code, timezone)
    VALUES
      ('Duplicate Country', '중복 국가', 'Duplicate Country', 'duplicate-country', 'country', 'ZZ', 'UTC');
    RAISE EXCEPTION 'Smoke test failed: duplicate country_code for a country was accepted.';
  EXCEPTION
    WHEN unique_violation THEN NULL;
  END;

  BEGIN
    UPDATE public.regions SET country_code = 'XZ' WHERE id = v_country_id;
    RAISE EXCEPTION 'Smoke test failed: root country_code change ignored existing children.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    UPDATE public.regions SET parent_id = v_area_id WHERE id = v_city_id;
    RAISE EXCEPTION 'Smoke test failed: region cycle was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO public.regions
      (parent_id, name_local, slug, region_type, country_code)
    VALUES
      (v_country_id, 'Wrong Country Code', 'wrong-country-code', 'city', 'YY');
    RAISE EXCEPTION 'Smoke test failed: parent/child country mismatch was accepted.';
  EXCEPTION
    WHEN check_violation THEN NULL;
  END;

  INSERT INTO public.places (region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_area_id, 'Smoke Cafe', '테스트 카페', 'cafe', v_user_id)
  RETURNING id INTO v_place_id;

  INSERT INTO public.places (region_id, name_local, name_ko, category, created_by_user_id)
  VALUES (v_other_city_id, 'Other Cafe', '다른 카페', 'cafe', v_user_id)
  RETURNING id INTO v_other_place_id;

  BEGIN
    INSERT INTO public.posts (author_id, main_region_id, title, content, status)
    VALUES (v_user_id, v_city_id, '장소 없는 글', '공개되면 안 됩니다.', 'draft')
    RETURNING id INTO v_bad_post_id;

    UPDATE public.posts SET status = 'published' WHERE id = v_bad_post_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'Smoke test failed: published post without a place was accepted.';
  EXCEPTION
    WHEN check_violation THEN
      SET CONSTRAINTS ALL DEFERRED;
  END;

  BEGIN
    INSERT INTO public.posts (author_id, main_region_id, title, content, status)
    VALUES (v_user_id, v_city_id, '지역 불일치 글', '다른 국가 장소를 연결합니다.', 'draft')
    RETURNING id INTO v_bad_post_id;

    INSERT INTO public.post_places (post_id, place_id, sort_order)
    VALUES (v_bad_post_id, v_other_place_id, 0);

    UPDATE public.posts SET status = 'published' WHERE id = v_bad_post_id;
    SET CONSTRAINTS ALL IMMEDIATE;
    RAISE EXCEPTION 'Smoke test failed: place outside main region was accepted.';
  EXCEPTION
    WHEN check_violation THEN
      SET CONSTRAINTS ALL DEFERRED;
  END;

  INSERT INTO public.posts (author_id, main_region_id, title, content, status)
  VALUES (v_user_id, v_city_id, '정상 게시글', '장소가 연결된 정상 게시글입니다.', 'draft')
  RETURNING id INTO v_post_id;

  INSERT INTO public.post_places (post_id, place_id, sort_order, memo)
  VALUES (v_post_id, v_place_id, 0, '장소 메모');

  INSERT INTO public.post_images (post_id, image_url, sort_order)
  VALUES (v_post_id, '/smoke-test/image-1.jpg', 0);

  UPDATE public.posts SET status = 'published' WHERE id = v_post_id;
  SET CONSTRAINTS ALL IMMEDIATE;
  SET CONSTRAINTS ALL DEFERRED;

  SELECT count(*) INTO v_count
  FROM public.get_region_descendants(v_country_id)
  WHERE region_id IN (v_country_id, v_city_id, v_area_id);

  IF v_count <> 3 THEN
    RAISE EXCEPTION 'Smoke test failed: expected 3 region descendants, got %.', v_count;
  END IF;

  IF NOT public.is_region_ancestor_or_same(v_city_id, v_area_id) THEN
    RAISE EXCEPTION 'Smoke test failed: region ancestor check returned false.';
  END IF;

  INSERT INTO public.post_likes (post_id, user_id) VALUES (v_post_id, v_user_id);
  INSERT INTO public.bookmarks (post_id, user_id) VALUES (v_post_id, v_user_id);
  INSERT INTO public.comments (post_id, author_id, content) VALUES (v_post_id, v_user_id, '테스트 댓글');

  SELECT
    (SELECT count(*) FROM public.post_likes WHERE post_id = v_post_id)
    + (SELECT count(*) FROM public.bookmarks WHERE post_id = v_post_id)
    + (SELECT count(*) FROM public.comments WHERE post_id = v_post_id AND deleted_at IS NULL)
  INTO v_count;

  IF v_count <> 3 THEN
    RAISE EXCEPTION 'Smoke test failed: expected metric total 3, got %.', v_count;
  END IF;

  UPDATE public.posts SET view_count = view_count + 1 WHERE id = v_post_id;
  IF (SELECT view_count FROM public.posts WHERE id = v_post_id) <> 1 THEN
    RAISE EXCEPTION 'Smoke test failed: view_count did not increase.';
  END IF;

  UPDATE public.posts SET status = 'deleted' WHERE id = v_post_id;
  IF NOT EXISTS (
    SELECT 1 FROM public.posts
    WHERE id = v_post_id
      AND deleted_at IS NOT NULL
      AND purge_after = deleted_at + interval '1 year'
  ) THEN
    RAISE EXCEPTION 'Smoke test failed: one-year retention was not set.';
  END IF;
END;
$$;

ROLLBACK;
