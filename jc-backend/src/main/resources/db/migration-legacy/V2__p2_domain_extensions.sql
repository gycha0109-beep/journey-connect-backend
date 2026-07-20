-- P2: 지역 표준화/PostGIS, 다중 이미지, 크루 승인 상태, 리프레시 토큰을 추가합니다.
-- 첫 단계로 PostGIS 확장과 표준 region 테이블을 준비하고, 기존 문자열 지역명을 정규화된 코드로 이관합니다.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS region (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    center geometry(Point, 4326),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_region_code ON region (code);
CREATE INDEX IF NOT EXISTS idx_region_country ON region (country_code);
CREATE INDEX IF NOT EXISTS idx_region_display_name ON region (display_name);
CREATE INDEX IF NOT EXISTS idx_region_center_gist ON region USING GIST (center);

INSERT INTO region (code, country_code, display_name, center)
VALUES
    ('KR-SEOUL', 'KR', 'Seoul', ST_SetSRID(ST_MakePoint(126.9780, 37.5665), 4326)),
    ('KR-BUSAN', 'KR', 'Busan', ST_SetSRID(ST_MakePoint(129.0756, 35.1796), 4326)),
    ('KR-JEJU', 'KR', 'Jeju', ST_SetSRID(ST_MakePoint(126.5312, 33.4996), 4326)),
    ('JP-TOKYO', 'JP', 'Tokyo', ST_SetSRID(ST_MakePoint(139.6917, 35.6895), 4326))
ON CONFLICT (code) DO NOTHING;

-- 기존 자유 문자열 지역명은 삭제하지 않고 LEGACY 코드로 표준 테이블에 이관합니다.
-- 이렇게 분리해 두면 기존 데이터가 완전히 사라지지 않으면서도 새 API가 region_id 기준으로 조회할 수 있습니다.
WITH legacy_names AS (
    SELECT DISTINCT trim(region_name) AS region_name FROM journey_post WHERE trim(region_name) <> ''
    UNION
    SELECT DISTINCT trim(region_name) AS region_name FROM crew WHERE trim(region_name) <> ''
)
INSERT INTO region (code, country_code, display_name)
SELECT
    'LEGACY-' || upper(substr(md5(region_name), 1, 12)),
    'ZZ',
    region_name
FROM legacy_names names
WHERE NOT EXISTS (
    SELECT 1 FROM region r WHERE lower(r.display_name) = lower(names.region_name)
)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE journey_post ALTER COLUMN region_name TYPE VARCHAR(100);
ALTER TABLE journey_post ADD COLUMN IF NOT EXISTS region_id BIGINT;
UPDATE journey_post p
SET region_id = (
    SELECT r.id
    FROM region r
    WHERE lower(r.display_name) = lower(p.region_name)
    ORDER BY CASE WHEN r.country_code = 'ZZ' THEN 1 ELSE 0 END, r.id
    LIMIT 1
)
WHERE p.region_id IS NULL;

ALTER TABLE crew ALTER COLUMN region_name TYPE VARCHAR(100);
ALTER TABLE crew ADD COLUMN IF NOT EXISTS region_id BIGINT;
UPDATE crew c
SET region_id = (
    SELECT r.id
    FROM region r
    WHERE lower(r.display_name) = lower(c.region_name)
    ORDER BY CASE WHEN r.country_code = 'ZZ' THEN 1 ELSE 0 END, r.id
    LIMIT 1
)
WHERE c.region_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM journey_post WHERE region_id IS NULL) THEN
        ALTER TABLE journey_post ALTER COLUMN region_id SET NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM crew WHERE region_id IS NULL) THEN
        ALTER TABLE crew ALTER COLUMN region_id SET NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_post_region') THEN
        ALTER TABLE journey_post
            ADD CONSTRAINT fk_post_region FOREIGN KEY (region_id) REFERENCES region(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_crew_region') THEN
        ALTER TABLE crew
            ADD CONSTRAINT fk_crew_region FOREIGN KEY (region_id) REFERENCES region(id);
    END IF;
END $$;

DROP INDEX IF EXISTS idx_post_region_name;
DROP INDEX IF EXISTS idx_crew_region_name;
DROP INDEX IF EXISTS idx_post_region;
DROP INDEX IF EXISTS idx_crew_region;
CREATE INDEX IF NOT EXISTS idx_post_region ON journey_post (region_id);
CREATE INDEX IF NOT EXISTS idx_post_feed_cursor ON journey_post (published, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_crew_region ON crew (region_id);

CREATE TABLE IF NOT EXISTS post_image (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES journey_post(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL,
    alt_text VARCHAR(200)
);
CREATE INDEX IF NOT EXISTS idx_post_image_order ON post_image (post_id, sort_order);
INSERT INTO post_image (post_id, image_url, sort_order)
SELECT p.id, p.cover_image_url, 0
FROM journey_post p
WHERE p.cover_image_url IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM post_image i WHERE i.post_id = p.id);

-- 크루 승인 정책은 기본적으로 필요하도록 설정하고, 참가 신청 상태와 리뷰 정보를 함께 확장합니다.
ALTER TABLE crew ADD COLUMN IF NOT EXISTS approval_required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE crew_member ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE crew_member ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE crew_member ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE crew_member ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE crew_member SET status = 'APPROVED' WHERE status = 'JOINED';
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_crew_member_reviewer') THEN
        ALTER TABLE crew_member
            ADD CONSTRAINT fk_crew_member_reviewer FOREIGN KEY (reviewed_by) REFERENCES user_account(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_token (expires_at);
