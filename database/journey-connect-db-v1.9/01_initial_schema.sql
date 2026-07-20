-- Journey Connect DB v1.8 - Base schema
-- Target: PostgreSQL 15+
-- Run once in an empty local database through DBeaver.

BEGIN;

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$;

CREATE TABLE public.app_users (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email varchar(320) NOT NULL,
  password_hash varchar(255) NOT NULL,
  username varchar(30) NOT NULL,
  display_name varchar(40) NOT NULL,
  profile_image_url text,
  bio varchar(500) NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT app_users_email_basic_check
    CHECK (char_length(email) BETWEEN 3 AND 320 AND position('@' IN email) > 1),
  CONSTRAINT app_users_username_format_check
    CHECK (username ~ '^[a-z0-9_]{3,30}$'),
  CONSTRAINT app_users_display_name_length_check
    CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 40)
);

CREATE UNIQUE INDEX app_users_email_ci_uq ON public.app_users (lower(email));
CREATE UNIQUE INDEX app_users_username_ci_uq ON public.app_users (lower(username));

CREATE TRIGGER app_users_set_updated_at
BEFORE UPDATE ON public.app_users
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE public.regions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  parent_id bigint REFERENCES public.regions(id) ON DELETE RESTRICT,
  name_local varchar(100) NOT NULL,
  name_ko varchar(100),
  name_en varchar(100),
  slug varchar(160) NOT NULL UNIQUE,
  region_type varchar(20) NOT NULL
    CHECK (region_type IN ('country', 'state', 'city', 'district', 'neighborhood', 'other')),
  country_code char(2) NOT NULL,
  timezone varchar(64),
  sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT regions_root_country_check CHECK (
    (parent_id IS NULL AND region_type = 'country')
    OR (parent_id IS NOT NULL AND region_type <> 'country')
  ),
  CONSTRAINT regions_not_self_parent_check CHECK (parent_id IS NULL OR parent_id <> id),
  CONSTRAINT regions_slug_format_check CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  CONSTRAINT regions_country_code_format_check CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX regions_parent_id_idx ON public.regions (parent_id, sort_order, name_local);
CREATE INDEX regions_name_local_lower_idx ON public.regions (lower(name_local));
CREATE INDEX regions_name_ko_lower_idx ON public.regions (lower(name_ko)) WHERE name_ko IS NOT NULL;
CREATE INDEX regions_name_en_lower_idx ON public.regions (lower(name_en)) WHERE name_en IS NOT NULL;
CREATE INDEX regions_country_code_idx ON public.regions (country_code, region_type, is_active);
CREATE UNIQUE INDEX regions_country_code_country_uq
ON public.regions (country_code)
WHERE region_type = 'country';

CREATE OR REPLACE FUNCTION public.validate_region_hierarchy()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent_country_code char(2);
  v_cycle_found boolean;
BEGIN
  -- A root region has no parent to validate, but changing its country code must still
  -- remain compatible with all existing direct children. This check must occur before
  -- the root early-return path.
  IF TG_OP = 'UPDATE'
     AND NEW.country_code IS DISTINCT FROM OLD.country_code
     AND EXISTS (
       SELECT 1
       FROM public.regions child
       WHERE child.parent_id = NEW.id
         AND child.country_code <> NEW.country_code
     ) THEN
    RAISE EXCEPTION 'Region country_code cannot differ from existing child regions.'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.parent_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT r.country_code
    INTO v_parent_country_code
  FROM public.regions r
  WHERE r.id = NEW.parent_id;

  IF v_parent_country_code IS NULL THEN
    RAISE EXCEPTION 'Parent region % does not exist.', NEW.parent_id
      USING ERRCODE = '23503';
  END IF;

  IF v_parent_country_code <> NEW.country_code THEN
    RAISE EXCEPTION 'Region country_code % must match parent country_code %.',
      NEW.country_code, v_parent_country_code
      USING ERRCODE = '23514';
  END IF;

  IF TG_OP = 'UPDATE' THEN
    WITH RECURSIVE ancestors AS (
      SELECT r.id, r.parent_id
      FROM public.regions r
      WHERE r.id = NEW.parent_id

      UNION ALL

      SELECT r.id, r.parent_id
      FROM public.regions r
      JOIN ancestors a ON r.id = a.parent_id
    )
    SELECT EXISTS (SELECT 1 FROM ancestors WHERE id = NEW.id)
      INTO v_cycle_found;

    IF v_cycle_found THEN
      RAISE EXCEPTION 'Region hierarchy cycle detected for region %.', NEW.id
        USING ERRCODE = '23514';
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER regions_validate_hierarchy
BEFORE INSERT OR UPDATE OF parent_id, country_code, region_type ON public.regions
FOR EACH ROW EXECUTE FUNCTION public.validate_region_hierarchy();

CREATE TRIGGER regions_set_updated_at
BEFORE UPDATE ON public.regions
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE FUNCTION public.get_region_descendants(p_region_id bigint)
RETURNS TABLE (region_id bigint, depth integer)
LANGUAGE sql
STABLE
AS $$
  WITH RECURSIVE region_tree AS (
    SELECT r.id, 0 AS depth, ARRAY[r.id]::bigint[] AS visited
    FROM public.regions r
    WHERE r.id = p_region_id AND r.is_active = true

    UNION ALL

    SELECT child.id, parent.depth + 1, parent.visited || child.id
    FROM region_tree parent
    JOIN public.regions child ON child.parent_id = parent.id
    WHERE child.is_active = true
      AND NOT child.id = ANY(parent.visited)
  )
  SELECT id AS region_id, depth
  FROM region_tree;
$$;

CREATE OR REPLACE FUNCTION public.is_region_ancestor_or_same(
  p_ancestor_region_id bigint,
  p_descendant_region_id bigint
)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  WITH RECURSIVE ancestors AS (
    SELECT r.id, r.parent_id, ARRAY[r.id]::bigint[] AS visited
    FROM public.regions r
    WHERE r.id = p_descendant_region_id

    UNION ALL

    SELECT parent.id, parent.parent_id, a.visited || parent.id
    FROM ancestors a
    JOIN public.regions parent ON parent.id = a.parent_id
    WHERE NOT parent.id = ANY(a.visited)
  )
  SELECT EXISTS (
    SELECT 1 FROM ancestors WHERE id = p_ancestor_region_id
  );
$$;

CREATE TABLE public.places (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  region_id bigint NOT NULL REFERENCES public.regions(id) ON DELETE RESTRICT,
  name_local varchar(200) NOT NULL,
  name_ko varchar(200),
  name_en varchar(200),
  normalized_name varchar(200) NOT NULL DEFAULT '',
  address text,
  latitude numeric(9, 6),
  longitude numeric(9, 6),
  category varchar(50),
  created_by_user_id bigint REFERENCES public.app_users(id) ON DELETE SET NULL,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT places_name_length_check CHECK (char_length(btrim(name_local)) BETWEEN 1 AND 200),
  CONSTRAINT places_latitude_check CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  CONSTRAINT places_longitude_check CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE OR REPLACE FUNCTION public.normalize_place_name()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.normalized_name := lower(regexp_replace(btrim(NEW.name_local), '\s+', '', 'g'));
  RETURN NEW;
END;
$$;

CREATE TRIGGER places_normalize_name
BEFORE INSERT OR UPDATE OF name_local ON public.places
FOR EACH ROW EXECUTE FUNCTION public.normalize_place_name();

CREATE TRIGGER places_set_updated_at
BEFORE UPDATE ON public.places
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE INDEX places_region_id_idx ON public.places (region_id, is_active, name_local);
CREATE INDEX places_normalized_name_idx ON public.places (normalized_name);
CREATE INDEX places_coordinates_idx ON public.places (latitude, longitude)
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE TABLE public.posts (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  author_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
  main_region_id bigint REFERENCES public.regions(id) ON DELETE RESTRICT,
  title varchar(150) NOT NULL DEFAULT '',
  content text NOT NULL DEFAULT '',
  view_count bigint NOT NULL DEFAULT 0 CHECK (view_count >= 0),
  visibility varchar(20) NOT NULL DEFAULT 'public'
    CHECK (visibility IN ('public', 'followers', 'private')),
  status varchar(20) NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'published', 'deleted')),
  published_at timestamptz,
  deleted_at timestamptz,
  purge_after timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT posts_published_content_check CHECK (
    status <> 'published'
    OR (
      main_region_id IS NOT NULL
      AND char_length(btrim(title)) BETWEEN 1 AND 150
      AND char_length(btrim(content)) >= 1
      AND published_at IS NOT NULL
    )
  ),
  CONSTRAINT posts_delete_lifecycle_check CHECK (
    (status = 'deleted' AND deleted_at IS NOT NULL AND purge_after IS NOT NULL AND purge_after > deleted_at)
    OR
    (status <> 'deleted' AND deleted_at IS NULL AND purge_after IS NULL)
  )
);

CREATE OR REPLACE FUNCTION public.set_post_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.status = 'published' AND NEW.published_at IS NULL THEN
    NEW.published_at := CURRENT_TIMESTAMP;
  END IF;

  IF NEW.status = 'deleted' THEN
    IF TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'deleted' THEN
      NEW.deleted_at := CURRENT_TIMESTAMP;
    ELSE
      NEW.deleted_at := OLD.deleted_at;
    END IF;
    NEW.purge_after := NEW.deleted_at + interval '1 year';
  ELSE
    NEW.deleted_at := NULL;
    NEW.purge_after := NULL;
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER posts_set_lifecycle
BEFORE INSERT OR UPDATE OF status, deleted_at, purge_after ON public.posts
FOR EACH ROW EXECUTE FUNCTION public.set_post_lifecycle();

CREATE TRIGGER posts_set_updated_at
BEFORE UPDATE ON public.posts
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE INDEX posts_author_published_idx ON public.posts (author_id, published_at DESC, id DESC)
WHERE status = 'published';
CREATE INDEX posts_region_feed_idx ON public.posts (main_region_id, published_at DESC, id DESC)
WHERE status = 'published' AND visibility = 'public';
CREATE INDEX posts_public_feed_idx ON public.posts (published_at DESC, id DESC)
WHERE status = 'published' AND visibility = 'public';
CREATE INDEX posts_purge_after_idx ON public.posts (purge_after) WHERE status = 'deleted';

CREATE TABLE public.post_images (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  image_url text NOT NULL,
  alt_text varchar(300),
  caption varchar(500),
  sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  width integer,
  height integer,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT post_images_url_not_blank_check CHECK (char_length(btrim(image_url)) > 0),
  CONSTRAINT post_images_dimensions_check CHECK (
    (width IS NULL AND height IS NULL)
    OR (width IS NOT NULL AND height IS NOT NULL AND width > 0 AND height > 0)
  ),
  UNIQUE (post_id, sort_order)
);


CREATE TABLE public.post_places (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  place_id bigint NOT NULL REFERENCES public.places(id) ON DELETE RESTRICT,
  sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  memo varchar(500),
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (post_id, place_id),
  UNIQUE (post_id, sort_order)
);

CREATE INDEX post_places_place_id_idx ON public.post_places (place_id, post_id);

CREATE OR REPLACE FUNCTION public.assert_published_post_integrity(p_post_id bigint)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_status varchar(20);
  v_main_region_id bigint;
  v_invalid_place_id bigint;
BEGIN
  SELECT p.status, p.main_region_id
    INTO v_status, v_main_region_id
  FROM public.posts p
  WHERE p.id = p_post_id;

  IF NOT FOUND OR v_status <> 'published' THEN
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.post_places pp WHERE pp.post_id = p_post_id
  ) THEN
    RAISE EXCEPTION 'Published post % must have at least one place.', p_post_id
      USING ERRCODE = '23514';
  END IF;

  SELECT pp.place_id
    INTO v_invalid_place_id
  FROM public.post_places pp
  JOIN public.places pl ON pl.id = pp.place_id
  WHERE pp.post_id = p_post_id
    AND NOT public.is_region_ancestor_or_same(v_main_region_id, pl.region_id)
  LIMIT 1;

  IF v_invalid_place_id IS NOT NULL THEN
    RAISE EXCEPTION 'Published post % has place % outside its main region hierarchy.',
      p_post_id, v_invalid_place_id
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_post_integrity_from_posts_trigger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM public.assert_published_post_integrity(NEW.id);
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_post_integrity_from_post_places_trigger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    PERFORM public.assert_published_post_integrity(OLD.post_id);
  ELSIF TG_OP = 'INSERT' THEN
    PERFORM public.assert_published_post_integrity(NEW.post_id);
  ELSE
    PERFORM public.assert_published_post_integrity(OLD.post_id);
    IF NEW.post_id IS DISTINCT FROM OLD.post_id THEN
      PERFORM public.assert_published_post_integrity(NEW.post_id);
    END IF;
  END IF;
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_post_integrity_from_places_trigger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_post_id bigint;
BEGIN
  FOR v_post_id IN
    SELECT DISTINCT pp.post_id
    FROM public.post_places pp
    JOIN public.posts p ON p.id = pp.post_id
    WHERE pp.place_id = NEW.id
      AND p.status = 'published'
  LOOP
    PERFORM public.assert_published_post_integrity(v_post_id);
  END LOOP;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER posts_require_valid_places_on_publish
AFTER INSERT OR UPDATE OF status, main_region_id ON public.posts
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_post_integrity_from_posts_trigger();

CREATE CONSTRAINT TRIGGER post_places_preserve_published_post_integrity
AFTER INSERT OR UPDATE OR DELETE ON public.post_places
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_post_integrity_from_post_places_trigger();

CREATE CONSTRAINT TRIGGER places_preserve_published_post_integrity
AFTER UPDATE OF region_id ON public.places
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_post_integrity_from_places_trigger();

CREATE OR REPLACE FUNCTION public.check_post_integrity_from_regions_trigger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_post_id bigint;
BEGIN
  FOR v_post_id IN
    SELECT p.id
    FROM public.posts p
    WHERE p.status = 'published'
  LOOP
    PERFORM public.assert_published_post_integrity(v_post_id);
  END LOOP;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER regions_preserve_published_post_integrity
AFTER UPDATE OF parent_id ON public.regions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_post_integrity_from_regions_trigger();

CREATE TABLE public.tags (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug varchar(60) NOT NULL UNIQUE,
  name_ko varchar(50) NOT NULL,
  name_en varchar(50),
  is_active boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT tags_slug_format_check CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  CONSTRAINT tags_name_ko_length_check CHECK (char_length(btrim(name_ko)) BETWEEN 1 AND 50)
);

CREATE TRIGGER tags_set_updated_at
BEFORE UPDATE ON public.tags
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE public.post_tags (
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  tag_id bigint NOT NULL REFERENCES public.tags(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX post_tags_tag_id_idx ON public.post_tags (tag_id, post_id);

CREATE TABLE public.comments (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  author_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
  content varchar(1000) NOT NULL,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT comments_content_length_check CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000)
);

CREATE TRIGGER comments_set_updated_at
BEFORE UPDATE ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE INDEX comments_post_created_idx ON public.comments (post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX comments_author_id_idx ON public.comments (author_id, created_at DESC);

CREATE TABLE public.post_likes (
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, user_id)
);
CREATE INDEX post_likes_user_id_idx ON public.post_likes (user_id, created_at DESC);

CREATE TABLE public.bookmarks (
  post_id bigint NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, user_id)
);
CREATE INDEX bookmarks_user_id_idx ON public.bookmarks (user_id, created_at DESC);

CREATE TABLE public.follows (
  follower_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  following_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, following_id),
  CONSTRAINT follows_not_self_check CHECK (follower_id <> following_id)
);
CREATE INDEX follows_following_id_idx ON public.follows (following_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.purge_expired_deleted_posts()
RETURNS bigint
LANGUAGE plpgsql
AS $$
DECLARE
  v_deleted_count bigint;
BEGIN
  WITH deleted_rows AS (
    DELETE FROM public.posts
    WHERE status = 'deleted' AND purge_after <= CURRENT_TIMESTAMP
    RETURNING id
  )
  SELECT count(*) INTO v_deleted_count FROM deleted_rows;
  RETURN v_deleted_count;
END;
$$;

COMMIT;
