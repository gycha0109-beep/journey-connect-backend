-- Journey Connect DB v1.8 - Optional seed
-- Run after 01_initial_schema.sql.

BEGIN;

INSERT INTO public.tags (slug, name_ko, name_en, sort_order)
VALUES
  ('solo-travel', '혼자여행', 'Solo travel', 10),
  ('couple-trip', '데이트', 'Couple trip', 20),
  ('family-trip', '가족여행', 'Family trip', 30),
  ('food', '맛집', 'Food', 40),
  ('cafe', '카페', 'Cafe', 50),
  ('nature', '자연', 'Nature', 60)
ON CONFLICT (slug) DO UPDATE
SET name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
VALUES
  ('대한민국', '대한민국', 'South Korea', 'kr', 'country', 'KR', 'Asia/Seoul', 10),
  ('日本', '일본', 'Japan', 'jp', 'country', 'JP', 'Asia/Tokyo', 20),
  ('Italia', '이탈리아', 'Italy', 'it', 'country', 'IT', 'Europe/Rome', 30),
  ('中国', '중국', 'China', 'cn', 'country', 'CN', 'Asia/Shanghai', 40)
ON CONFLICT (slug) DO UPDATE
SET name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '서울', '서울', 'Seoul', 'kr-seoul', 'city', 'KR', 'Asia/Seoul', 10
FROM public.regions WHERE slug = 'kr'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '부산', '부산', 'Busan', 'kr-busan', 'city', 'KR', 'Asia/Seoul', 20
FROM public.regions WHERE slug = 'kr'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '성수', '성수', 'Seongsu', 'kr-seoul-seongsu', 'neighborhood', 'KR', 'Asia/Seoul', 10
FROM public.regions WHERE slug = 'kr-seoul'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '강남', '강남', 'Gangnam', 'kr-seoul-gangnam', 'district', 'KR', 'Asia/Seoul', 20
FROM public.regions WHERE slug = 'kr-seoul'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '광안리', '광안리', 'Gwangalli', 'kr-busan-gwangalli', 'neighborhood', 'KR', 'Asia/Seoul', 10
FROM public.regions WHERE slug = 'kr-busan'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '東京', '도쿄', 'Tokyo', 'jp-tokyo', 'city', 'JP', 'Asia/Tokyo', 10
FROM public.regions WHERE slug = 'jp'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, 'Milano', '밀라노', 'Milan', 'it-milan', 'city', 'IT', 'Europe/Rome', 10
FROM public.regions WHERE slug = 'it'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '北京', '베이징', 'Beijing', 'cn-beijing', 'city', 'CN', 'Asia/Shanghai', 10
FROM public.regions WHERE slug = 'cn'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko,
    name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type,
    country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone,
    sort_order = EXCLUDED.sort_order,
    is_active = true;

COMMIT;
