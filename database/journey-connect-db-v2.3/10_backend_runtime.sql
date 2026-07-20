-- Journey Connect DB v2.0 - Spring backend runtime schema convergence
-- Target: PostgreSQL 15+
-- Prerequisite: 01_initial_schema.sql through 08_recommendation_security_roles.sql

BEGIN;

-- The canonical region hierarchy intentionally avoids a PostGIS dependency. Optional
-- center coordinates preserve the current nearby-region API with ordinary PostgreSQL.
ALTER TABLE public.regions
  ADD COLUMN center_latitude numeric(9, 6),
  ADD COLUMN center_longitude numeric(9, 6),
  ADD CONSTRAINT regions_center_coordinates_check CHECK (
    (center_latitude IS NULL AND center_longitude IS NULL)
    OR (
      center_latitude IS NOT NULL
      AND center_longitude IS NOT NULL
      AND center_latitude BETWEEN -90 AND 90
      AND center_longitude BETWEEN -180 AND 180
    )
  );

CREATE INDEX regions_center_coordinates_idx
ON public.regions (center_latitude, center_longitude)
WHERE center_latitude IS NOT NULL AND center_longitude IS NOT NULL;

-- Regions used by the existing backend API and recommendation vocabulary.
INSERT INTO public.regions
  (parent_id, name_local, name_ko, name_en, slug, region_type, country_code, timezone, sort_order)
SELECT id, '제주', '제주', 'Jeju', 'kr-jeju', 'state', 'KR', 'Asia/Seoul', 30
FROM public.regions WHERE slug = 'kr'
ON CONFLICT (slug) DO UPDATE
SET parent_id = EXCLUDED.parent_id, name_local = EXCLUDED.name_local,
    name_ko = EXCLUDED.name_ko, name_en = EXCLUDED.name_en,
    region_type = EXCLUDED.region_type, country_code = EXCLUDED.country_code,
    timezone = EXCLUDED.timezone, sort_order = EXCLUDED.sort_order, is_active = true;

-- The current public API treats display names as unique nicknames. Keep the database
-- concurrency guarantee case-insensitive rather than relying only on a service check.
CREATE UNIQUE INDEX app_users_display_name_ci_uq
ON public.app_users (lower(display_name));

CREATE TABLE public.refresh_tokens (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  token_hash varchar(64) NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT refresh_tokens_hash_format_check CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT refresh_tokens_expiration_check CHECK (expires_at > created_at),
  CONSTRAINT refresh_tokens_revocation_check CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE UNIQUE INDEX refresh_tokens_hash_uq ON public.refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_idx ON public.refresh_tokens (user_id, created_at DESC);
CREATE INDEX refresh_tokens_expiry_idx ON public.refresh_tokens (expires_at)
WHERE revoked_at IS NULL;

CREATE TRIGGER refresh_tokens_set_updated_at
BEFORE UPDATE ON public.refresh_tokens
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE public.crews (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  owner_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
  region_id bigint NOT NULL REFERENCES public.regions(id) ON DELETE RESTRICT,
  title varchar(120) NOT NULL,
  description text NOT NULL,
  travel_date date,
  capacity integer NOT NULL,
  recruiting boolean NOT NULL DEFAULT true,
  approval_required boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT crews_title_length_check CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
  CONSTRAINT crews_description_not_blank_check CHECK (char_length(btrim(description)) > 0),
  CONSTRAINT crews_capacity_check CHECK (capacity BETWEEN 2 AND 100)
);

CREATE INDEX crews_region_feed_idx
ON public.crews (region_id, recruiting, travel_date, id DESC);
CREATE INDEX crews_owner_idx ON public.crews (owner_id, created_at DESC);

CREATE TRIGGER crews_set_updated_at
BEFORE UPDATE ON public.crews
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE public.crew_members (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  crew_id bigint NOT NULL REFERENCES public.crews(id) ON DELETE CASCADE,
  user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  status varchar(20) NOT NULL,
  reviewed_by bigint REFERENCES public.app_users(id) ON DELETE SET NULL,
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT crew_members_status_check CHECK (
    status IN ('OWNER', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
  ),
  CONSTRAINT crew_members_review_lifecycle_check CHECK (
    (status IN ('OWNER', 'PENDING') AND reviewed_by IS NULL AND reviewed_at IS NULL)
    OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    OR (status = 'CANCELLED' AND reviewed_by IS NULL)
  ),
  UNIQUE (crew_id, user_id)
);

CREATE INDEX crew_members_crew_status_idx
ON public.crew_members (crew_id, status, created_at, id);
CREATE INDEX crew_members_user_idx
ON public.crew_members (user_id, status, created_at DESC);

CREATE TRIGGER crew_members_set_updated_at
BEFORE UPDATE ON public.crew_members
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- A membership cannot claim OWNER for a different user than the crew owner.
CREATE OR REPLACE FUNCTION public.validate_crew_member_owner_binding()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_owner_id bigint;
BEGIN
  SELECT c.owner_id INTO v_owner_id
  FROM public.crews c
  WHERE c.id = NEW.crew_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Crew membership references missing crew %.', NEW.crew_id
      USING ERRCODE = '23503';
  END IF;

  IF NEW.status = 'OWNER' AND v_owner_id <> NEW.user_id THEN
    RAISE EXCEPTION 'Crew OWNER membership must match crews.owner_id.'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.status IN ('APPROVED', 'REJECTED') AND NEW.reviewed_by <> v_owner_id THEN
    RAISE EXCEPTION 'Crew review must be performed by crews.owner_id.'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER crew_members_validate_owner_binding
BEFORE INSERT OR UPDATE OF crew_id, user_id, status, reviewed_by ON public.crew_members
FOR EACH ROW EXECUTE FUNCTION public.validate_crew_member_owner_binding();


-- The crew aggregate must always have exactly one matching OWNER row and cannot
-- contain more active members than its configured capacity.
CREATE OR REPLACE FUNCTION public.assert_crew_membership_integrity(p_crew_id bigint)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_owner_id bigint;
  v_capacity integer;
  v_owner_count integer;
  v_active_count integer;
BEGIN
  SELECT owner_id, capacity INTO v_owner_id, v_capacity
  FROM public.crews
  WHERE id = p_crew_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  SELECT count(*) FILTER (WHERE status = 'OWNER' AND user_id = v_owner_id),
         count(*) FILTER (WHERE status IN ('OWNER', 'APPROVED'))
    INTO v_owner_count, v_active_count
  FROM public.crew_members
  WHERE crew_id = p_crew_id;

  IF v_owner_count <> 1 THEN
    RAISE EXCEPTION 'Crew % must have exactly one OWNER membership matching owner_id.', p_crew_id
      USING ERRCODE = '23514';
  END IF;

  IF v_active_count > v_capacity THEN
    RAISE EXCEPTION 'Crew % active member count % exceeds capacity %.',
      p_crew_id, v_active_count, v_capacity
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_crew_integrity_from_crew()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM public.assert_crew_membership_integrity(NEW.id);
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_crew_integrity_from_member()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM public.assert_crew_membership_integrity(
    CASE WHEN TG_OP = 'DELETE' THEN OLD.crew_id ELSE NEW.crew_id END
  );
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER crews_membership_integrity_check
AFTER INSERT OR UPDATE OF owner_id, capacity ON public.crews
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_crew_integrity_from_crew();

CREATE CONSTRAINT TRIGGER crew_members_aggregate_integrity_check
AFTER INSERT OR UPDATE OR DELETE ON public.crew_members
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.check_crew_integrity_from_member();

COMMIT;
