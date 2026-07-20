-- P0/P1 시점의 Hibernate 자동 생성 스키마를 Flyway 기준선으로 옮깁니다.
-- 기존 로컬 DB에도 적용할 수 있도록 CREATE TABLE/INDEX는 멱등 형태로 작성합니다.
-- 기본 사용자, 게시물, 좋아요/북마크, 댓글, 크루 도메인을 먼저 생성해 이후 확장 마이그레이션이
-- 안정적으로 참조할 수 있는 기반 구조를 만든다.

CREATE TABLE IF NOT EXISTS user_account (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    bio VARCHAR(300),
    profile_image_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON user_account (email);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_nickname ON user_account (nickname);

CREATE TABLE IF NOT EXISTS journey_post (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES user_account(id),
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    region_name VARCHAR(100) NOT NULL,
    cover_image_url VARCHAR(500),
    view_count BIGINT NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_post_created_at ON journey_post (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_post_region_name ON journey_post (region_name);

CREATE TABLE IF NOT EXISTS post_like (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES journey_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_post_like ON post_like (post_id, user_id);

CREATE TABLE IF NOT EXISTS bookmark (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES journey_post(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bookmark ON bookmark (post_id, user_id);

CREATE TABLE IF NOT EXISTS post_comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES journey_post(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_comment_post ON post_comment (post_id);

CREATE TABLE IF NOT EXISTS crew (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES user_account(id),
    title VARCHAR(120) NOT NULL,
    region_name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    travel_date DATE,
    capacity INTEGER NOT NULL,
    recruiting BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crew_region_name ON crew (region_name);

CREATE TABLE IF NOT EXISTS crew_member (
    id BIGSERIAL PRIMARY KEY,
    crew_id BIGINT NOT NULL REFERENCES crew(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_crew_member UNIQUE (crew_id, user_id)
);
