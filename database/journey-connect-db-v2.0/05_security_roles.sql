-- Journey Connect DB v1.8 - Database authorization and admin security
-- Target: PostgreSQL 15+
-- Prerequisite: 01_initial_schema.sql, 04_admin_support.sql
-- Run as a PostgreSQL superuser, or as a role that has both CREATEROLE and ownership of the database objects.
--
-- Request identity contract:
-- Every authenticated backend transaction must set the verified application user ID with:
--   SELECT set_config('jc.current_user_id', :verified_user_id::text, true);
-- The third argument MUST remain true so the value is transaction-local.
-- Client input must never be copied into this setting without server-side authentication.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_app') THEN
    CREATE ROLE jc_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_auth') THEN
    CREATE ROLE jc_auth NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_admin') THEN
    CREATE ROLE jc_admin NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_security_owner') THEN
    CREATE ROLE jc_security_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

-- Cluster roles are shared across databases. Existing same-name roles are accepted only
-- when they already have the expected safe attributes; a CREATEROLE migration account
-- cannot demote an unsafe superuser/replication role, so the migration fails closed.
DO $$
DECLARE
  v_unsafe_roles text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname)
    INTO v_unsafe_roles
  FROM pg_roles
  WHERE rolname IN ('jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner')
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);

  IF v_unsafe_roles IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe pre-existing Journey Connect role attributes: %. Normalize or drop these roles as a PostgreSQL superuser, then rerun.',
      v_unsafe_roles;
  END IF;
END;
$$;

-- No runtime role inherits another runtime role. Admin writes are available only through
-- explicitly granted SECURITY DEFINER functions.
REVOKE jc_app FROM jc_admin;
REVOKE jc_admin FROM jc_app;
REVOKE jc_auth FROM jc_app, jc_admin;

DO $$
DECLARE
  v_unexpected_memberships text;
BEGIN
  IF pg_has_role('jc_admin', 'jc_app', 'MEMBER') THEN
    RAISE EXCEPTION 'jc_admin must not inherit jc_app directly or indirectly.';
  END IF;

  SELECT string_agg(member_role.rolname || ' -> ' || granted_role.rolname, ', ' ORDER BY member_role.rolname, granted_role.rolname)
    INTO v_unexpected_memberships
  FROM pg_auth_members m
  JOIN pg_roles member_role ON member_role.oid = m.member
  JOIN pg_roles granted_role ON granted_role.oid = m.roleid
  WHERE member_role.rolname IN ('jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner');

  IF v_unexpected_memberships IS NOT NULL THEN
    RAISE EXCEPTION 'Journey Connect security roles must not inherit unrelated roles: %',
      v_unexpected_memberships;
  END IF;
END;
$$;

-- The migration owner needs membership only to transfer function ownership.
GRANT jc_security_owner TO CURRENT_USER;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

-- Clear privileges from earlier package revisions before rebuilding the exact matrix.
-- This makes rerunning the security migration converge instead of retaining broad v1.5 grants.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM jc_app, jc_auth, jc_admin;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM jc_app, jc_auth, jc_admin;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM jc_security_owner;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM jc_security_owner;

GRANT USAGE ON SCHEMA public TO jc_app, jc_auth, jc_admin, jc_security_owner;
GRANT CREATE ON SCHEMA public TO jc_security_owner;

-- Default privileges are role-specific. Apply them to the migration role and to the
-- dedicated owner of future privileged functions.
ALTER DEFAULT PRIVILEGES REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE jc_security_owner REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE jc_security_owner REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE jc_security_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- Public profile access deliberately excludes email, password_hash and role.
-- account_status is available to the shared backend for its mandatory common guard,
-- but must not be serialized into public profile responses.
GRANT SELECT (id, username, display_name, profile_image_url, bio,
              account_status, created_at, updated_at)
ON public.app_users TO jc_app;
GRANT UPDATE (username, display_name, profile_image_url, bio)
ON public.app_users TO jc_app;

-- Authentication backend only. Never expose this role or its query results to clients.
GRANT SELECT (id, email, password_hash, username, role, account_status)
ON public.app_users TO jc_auth;
GRANT INSERT (email, password_hash, username, display_name, profile_image_url, bio)
ON public.app_users TO jc_auth;
GRANT UPDATE (email, password_hash)
ON public.app_users TO jc_auth;

-- Normal application reads.
GRANT SELECT ON public.regions, public.places, public.tags, public.posts,
  public.post_images, public.post_places, public.post_tags, public.comments,
  public.post_likes, public.bookmarks, public.follows TO jc_app;

-- Normal application writes. Moderation-managed columns and lifecycle timestamps are
-- deliberately omitted. Row ownership and account-status checks still belong in the
-- Spring security/service layer because jc_app is a shared backend role.
GRANT INSERT (author_id, main_region_id, title, content, visibility, status)
ON public.posts TO jc_app;
GRANT UPDATE (main_region_id, title, content, visibility, status)
ON public.posts TO jc_app;

GRANT INSERT (post_id, author_id, content)
ON public.comments TO jc_app;
GRANT UPDATE (content, deleted_at)
ON public.comments TO jc_app;

GRANT INSERT, DELETE ON public.post_images, public.post_places,
  public.post_tags, public.post_likes, public.bookmarks, public.follows TO jc_app;
GRANT UPDATE (image_url, alt_text, caption, sort_order, width, height)
ON public.post_images TO jc_app;
GRANT UPDATE (place_id, sort_order, memo)
ON public.post_places TO jc_app;

GRANT INSERT (region_id, name_local, name_ko, name_en, address, latitude,
              longitude, category, created_by_user_id)
ON public.places TO jc_app;
-- Places are shared reference data. Normal users may register a new place, but
-- existing place rows are not directly editable through the shared application role.
REVOKE UPDATE ON public.places FROM jc_app;

-- Physical post/comment deletion is not an application operation. Posts use status
-- = deleted and comments use deleted_at. This also prevents open reports from being
-- erased through an ON DELETE CASCADE path.
REVOKE DELETE ON public.posts, public.comments FROM jc_app;

-- Identity sequences are granted one by one. Runtime roles cannot advance admin or
-- unrelated sequences.
GRANT USAGE ON SEQUENCE public.posts_id_seq, public.post_images_id_seq,
  public.post_places_id_seq, public.comments_id_seq, public.places_id_seq TO jc_app;
GRANT USAGE ON SEQUENCE public.app_users_id_seq TO jc_auth;

-- Admin runtime has read-only access to operating data. It has no direct content,
-- report, account-role or audit mutation privileges.
GRANT SELECT (id, email, username, display_name, profile_image_url, bio,
              created_at, updated_at, role, account_status)
ON public.app_users TO jc_admin;
GRANT SELECT ON public.regions, public.places, public.tags, public.posts,
  public.post_images, public.post_places, public.post_tags, public.comments,
  public.post_likes, public.bookmarks, public.follows,
  public.reports, public.admin_actions TO jc_admin;

REVOKE UPDATE (role, account_status) ON public.app_users FROM jc_app, jc_auth, jc_admin;
REVOKE INSERT, UPDATE, DELETE ON public.reports FROM jc_app, jc_auth, jc_admin;
REVOKE INSERT, UPDATE, DELETE ON public.admin_actions FROM jc_app, jc_auth, jc_admin;
REVOKE INSERT, UPDATE, DELETE ON public.posts, public.comments FROM jc_admin;

-- Least-privilege capabilities used only while privileged functions execute as
-- jc_security_owner. This role cannot log in.
GRANT SELECT (id, username, display_name, profile_image_url, bio, role, account_status)
ON public.app_users TO jc_security_owner;
GRANT UPDATE (role, account_status) ON public.app_users TO jc_security_owner;
GRANT SELECT, INSERT, UPDATE ON public.reports TO jc_security_owner;
GRANT SELECT, INSERT ON public.admin_actions TO jc_security_owner;
GRANT SELECT ON public.posts, public.comments, public.post_images,
  public.post_places, public.places, public.post_tags, public.tags, public.follows
TO jc_security_owner;
GRANT UPDATE (moderation_status, view_count) ON public.posts TO jc_security_owner;
GRANT UPDATE (moderation_deleted_at) ON public.comments TO jc_security_owner;
GRANT USAGE ON SEQUENCE public.reports_id_seq, public.admin_actions_id_seq
TO jc_security_owner;

CREATE OR REPLACE FUNCTION public.current_request_user_id()
RETURNS bigint
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
  v_raw text;
BEGIN
  v_raw := current_setting('jc.current_user_id', true);

  IF v_raw IS NULL
     OR btrim(v_raw) = ''
     OR char_length(v_raw) > 19
     OR v_raw !~ '^[0-9]+$' THEN
    RAISE EXCEPTION 'Authenticated request user context is missing or invalid.'
      USING ERRCODE = '42501';
  END IF;

  BEGIN
    RETURN v_raw::bigint;
  EXCEPTION WHEN numeric_value_out_of_range THEN
    RAISE EXCEPTION 'Authenticated request user context is outside bigint range.'
      USING ERRCODE = '42501';
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public.require_active_user()
RETURNS bigint
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
  v_status varchar(20);
BEGIN
  v_user_id := public.current_request_user_id();

  SELECT u.account_status
    INTO v_status
  FROM public.app_users u
  WHERE u.id = v_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Request user % does not exist.', v_user_id USING ERRCODE = '23503';
  END IF;
  IF v_status <> 'active' THEN
    RAISE EXCEPTION 'Request user % is not active.', v_user_id USING ERRCODE = '42501';
  END IF;

  RETURN v_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.require_staff_actor(
  p_admin_only boolean DEFAULT false
)
RETURNS TABLE (
  actor_user_id bigint,
  actor_role varchar(20),
  actor_username varchar(30)
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_user_id bigint;
BEGIN
  v_user_id := public.current_request_user_id();

  RETURN QUERY
  SELECT u.id, u.role, u.username
  FROM public.app_users u
  WHERE u.id = v_user_id
    AND u.account_status = 'active'
    AND u.role IN ('moderator', 'admin')
    AND (NOT p_admin_only OR u.role = 'admin');

  IF NOT FOUND THEN
    IF p_admin_only THEN
      RAISE EXCEPTION 'Action requires an active admin account.' USING ERRCODE = '42501';
    END IF;
    RAISE EXCEPTION 'Action requires an active moderator or admin account.' USING ERRCODE = '42501';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.assert_staff_target_allowed(
  p_actor_user_id bigint,
  p_actor_role varchar,
  p_target_user_id bigint
)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_target_role varchar(20);
BEGIN
  IF p_actor_user_id = p_target_user_id THEN
    RAISE EXCEPTION 'Staff account cannot apply this action to itself.' USING ERRCODE = '42501';
  END IF;

  SELECT u.role
    INTO v_target_role
  FROM public.app_users u
  WHERE u.id = p_target_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target user % does not exist.', p_target_user_id USING ERRCODE = '23503';
  END IF;

  IF p_actor_role = 'moderator' AND v_target_role IN ('moderator', 'admin') THEN
    RAISE EXCEPTION 'Moderator cannot act on a staff account.' USING ERRCODE = '42501';
  END IF;
END;
$$;

-- Shared visibility predicate for report submission and view-count updates.
-- NULL p_user_id represents an anonymous request and can see public posts only.
CREATE OR REPLACE FUNCTION public.can_user_view_post(
  p_user_id bigint,
  p_post_id bigint
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.posts p
    WHERE p.id = p_post_id
      AND p.status = 'published'
      AND p.moderation_status = 'visible'
      AND (
        p.visibility = 'public'
        OR (p_user_id IS NOT NULL AND p.author_id = p_user_id)
        OR (
          p_user_id IS NOT NULL
          AND p.visibility = 'followers'
          AND EXISTS (
            SELECT 1
            FROM public.follows f
            WHERE f.follower_id = p_user_id
              AND f.following_id = p.author_id
          )
        )
      )
  );
$$;

-- Moderated evidence cannot be rewritten through ordinary content UPDATE permissions.
CREATE OR REPLACE FUNCTION public.prevent_hidden_post_content_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF OLD.moderation_status = 'hidden'
     AND (
       NEW.main_region_id IS DISTINCT FROM OLD.main_region_id
       OR NEW.title IS DISTINCT FROM OLD.title
       OR NEW.content IS DISTINCT FROM OLD.content
       OR NEW.visibility IS DISTINCT FROM OLD.visibility
       OR NEW.status IS DISTINCT FROM OLD.status
     ) THEN
    RAISE EXCEPTION 'Moderation-hidden post content is immutable until staff restores it.'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS posts_prevent_hidden_content_mutation ON public.posts;
CREATE TRIGGER posts_prevent_hidden_content_mutation
BEFORE UPDATE ON public.posts
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_content_mutation();

CREATE OR REPLACE FUNCTION public.prevent_moderated_comment_content_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF OLD.moderation_deleted_at IS NOT NULL
     AND (
       NEW.content IS DISTINCT FROM OLD.content
       OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at
     ) THEN
    RAISE EXCEPTION 'Moderation-deleted comment content is immutable until staff restores it.'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS comments_prevent_moderated_content_mutation ON public.comments;
CREATE TRIGGER comments_prevent_moderated_content_mutation
BEFORE UPDATE ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.prevent_moderated_comment_content_mutation();

-- Images, place links and tags are part of a post's live presentation. Once staff hides
-- a post, ordinary child-row INSERT/UPDATE/DELETE operations must not rewrite what could
-- reappear after restoration. An AFTER DELETE trigger is used so a parent ON DELETE
-- CASCADE can proceed: by then the parent row is no longer visible to the child trigger.
CREATE OR REPLACE FUNCTION public.prevent_hidden_post_child_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_post_id bigint;
  v_old_post_id bigint;
BEGIN
  IF TG_OP = 'DELETE' THEN
    v_post_id := OLD.post_id;
  ELSE
    v_post_id := NEW.post_id;
    IF TG_OP = 'UPDATE' THEN
      v_old_post_id := OLD.post_id;
    END IF;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.posts p
    WHERE p.id = v_post_id
      AND p.moderation_status = 'hidden'
  ) OR (
    v_old_post_id IS NOT NULL
    AND v_old_post_id IS DISTINCT FROM v_post_id
    AND EXISTS (
      SELECT 1
      FROM public.posts p
      WHERE p.id = v_old_post_id
        AND p.moderation_status = 'hidden'
    )
  ) THEN
    RAISE EXCEPTION 'Child content of a moderation-hidden post is immutable until staff restores it.'
      USING ERRCODE = '42501';
  END IF;

  RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

DROP TRIGGER IF EXISTS post_images_prevent_hidden_mutation_before ON public.post_images;
CREATE TRIGGER post_images_prevent_hidden_mutation_before
BEFORE INSERT OR UPDATE ON public.post_images
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();
DROP TRIGGER IF EXISTS post_images_prevent_hidden_mutation_after_delete ON public.post_images;
CREATE TRIGGER post_images_prevent_hidden_mutation_after_delete
AFTER DELETE ON public.post_images
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();

DROP TRIGGER IF EXISTS post_places_prevent_hidden_mutation_before ON public.post_places;
CREATE TRIGGER post_places_prevent_hidden_mutation_before
BEFORE INSERT OR UPDATE ON public.post_places
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();
DROP TRIGGER IF EXISTS post_places_prevent_hidden_mutation_after_delete ON public.post_places;
CREATE TRIGGER post_places_prevent_hidden_mutation_after_delete
AFTER DELETE ON public.post_places
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();

DROP TRIGGER IF EXISTS post_tags_prevent_hidden_mutation_before ON public.post_tags;
CREATE TRIGGER post_tags_prevent_hidden_mutation_before
BEFORE INSERT OR UPDATE ON public.post_tags
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();
DROP TRIGGER IF EXISTS post_tags_prevent_hidden_mutation_after_delete ON public.post_tags;
CREATE TRIGGER post_tags_prevent_hidden_mutation_after_delete
AFTER DELETE ON public.post_tags
FOR EACH ROW EXECUTE FUNCTION public.prevent_hidden_post_child_mutation();

-- Audit rows are immutable after insertion.
CREATE OR REPLACE FUNCTION public.prevent_admin_action_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  RAISE EXCEPTION 'admin_actions is append-only; UPDATE and DELETE are forbidden.'
    USING ERRCODE = '42501';
END;
$$;

DROP TRIGGER IF EXISTS admin_actions_prevent_mutation ON public.admin_actions;
CREATE TRIGGER admin_actions_prevent_mutation
BEFORE UPDATE OR DELETE ON public.admin_actions
FOR EACH ROW EXECUTE FUNCTION public.prevent_admin_action_mutation();

CREATE OR REPLACE FUNCTION public.submit_report(
  p_target_type varchar,
  p_target_id bigint,
  p_reason_category varchar,
  p_reason_detail varchar DEFAULT ''
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_reporter_id bigint;
  v_report_id bigint;
  v_target_snapshot jsonb;
BEGIN
  v_reporter_id := public.require_active_user();

  IF p_target_type = 'user' THEN
    IF p_target_id = v_reporter_id THEN
      RAISE EXCEPTION 'A user cannot report their own account.' USING ERRCODE = '23514';
    END IF;

    SELECT jsonb_build_object(
      'type', 'user',
      'id', u.id,
      'username', u.username,
      'display_name', u.display_name,
      'profile_image_url', u.profile_image_url,
      'bio', u.bio,
      'role', u.role,
      'account_status', u.account_status
    )
    INTO v_target_snapshot
    FROM public.app_users u
    WHERE u.id = p_target_id;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'Reportable user % was not found.', p_target_id USING ERRCODE = 'P0002';
    END IF;

    INSERT INTO public.reports (
      reporter_id, target_type, target_entity_id, target_user_id,
      target_snapshot, reason_category, reason_detail
    ) VALUES (
      v_reporter_id, 'user', p_target_id, p_target_id,
      v_target_snapshot, p_reason_category, COALESCE(p_reason_detail, '')
    ) RETURNING id INTO v_report_id;

  ELSIF p_target_type = 'post' THEN
    SELECT jsonb_build_object(
      'type', 'post',
      'id', p.id,
      'author_id', p.author_id,
      'author_username', u.username,
      'main_region_id', p.main_region_id,
      'title', p.title,
      'content', p.content,
      'visibility', p.visibility,
      'status', p.status,
      'moderation_status', p.moderation_status,
      'published_at', p.published_at,
      'images', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'id', pi.id,
          'image_url', pi.image_url,
          'alt_text', pi.alt_text,
          'caption', pi.caption,
          'sort_order', pi.sort_order
        ) ORDER BY pi.sort_order, pi.id)
        FROM public.post_images pi
        WHERE pi.post_id = p.id
      ), '[]'::jsonb),
      'places', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'place_id', pl.id,
          'name_local', pl.name_local,
          'address', pl.address,
          'region_id', pl.region_id,
          'sort_order', pp.sort_order,
          'memo', pp.memo
        ) ORDER BY pp.sort_order, pp.id)
        FROM public.post_places pp
        JOIN public.places pl ON pl.id = pp.place_id
        WHERE pp.post_id = p.id
      ), '[]'::jsonb),
      'tags', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'tag_id', t.id,
          'slug', t.slug,
          'name_ko', t.name_ko
        ) ORDER BY t.sort_order, t.id)
        FROM public.post_tags pt
        JOIN public.tags t ON t.id = pt.tag_id
        WHERE pt.post_id = p.id
      ), '[]'::jsonb)
    )
    INTO v_target_snapshot
    FROM public.posts p
    JOIN public.app_users u ON u.id = p.author_id
    WHERE p.id = p_target_id
      AND p.author_id <> v_reporter_id
      AND public.can_user_view_post(v_reporter_id, p.id);

    IF NOT FOUND THEN
      RAISE EXCEPTION 'Accessible reportable post % was not found.', p_target_id USING ERRCODE = 'P0002';
    END IF;

    INSERT INTO public.reports (
      reporter_id, target_type, target_entity_id, target_post_id,
      target_snapshot, reason_category, reason_detail
    ) VALUES (
      v_reporter_id, 'post', p_target_id, p_target_id,
      v_target_snapshot, p_reason_category, COALESCE(p_reason_detail, '')
    ) RETURNING id INTO v_report_id;

  ELSIF p_target_type = 'comment' THEN
    SELECT jsonb_build_object(
      'type', 'comment',
      'id', c.id,
      'post_id', c.post_id,
      'post_title', p.title,
      'author_id', c.author_id,
      'author_username', u.username,
      'content', c.content,
      'created_at', c.created_at,
      'deleted_at', c.deleted_at,
      'moderation_deleted_at', c.moderation_deleted_at
    )
    INTO v_target_snapshot
    FROM public.comments c
    JOIN public.posts p ON p.id = c.post_id
    JOIN public.app_users u ON u.id = c.author_id
    WHERE c.id = p_target_id
      AND c.author_id <> v_reporter_id
      AND c.deleted_at IS NULL
      AND c.moderation_deleted_at IS NULL
      AND public.can_user_view_post(v_reporter_id, p.id);

    IF NOT FOUND THEN
      RAISE EXCEPTION 'Accessible reportable comment % was not found.', p_target_id USING ERRCODE = 'P0002';
    END IF;

    INSERT INTO public.reports (
      reporter_id, target_type, target_entity_id, target_comment_id,
      target_snapshot, reason_category, reason_detail
    ) VALUES (
      v_reporter_id, 'comment', p_target_id, p_target_id,
      v_target_snapshot, p_reason_category, COALESCE(p_reason_detail, '')
    ) RETURNING id INTO v_report_id;
  ELSE
    RAISE EXCEPTION 'Unsupported report target type: %', p_target_type USING ERRCODE = '23514';
  END IF;

  RETURN v_report_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_start_report_review(p_report_id bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_report_target_type varchar(20);
  v_report_target_id bigint;
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  UPDATE public.reports r
  SET status = 'in_review',
      handled_by_user_id = v_actor_user_id,
      handled_by_username = v_actor_username,
      handled_by_role = v_actor_role,
      handled_at = NULL,
      resolution_note = NULL
  WHERE r.id = p_report_id
    AND r.status = 'pending'
  RETURNING r.target_type, r.target_entity_id
  INTO v_report_target_type, v_report_target_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Pending report % was not found.', p_report_id USING ERRCODE = 'P0002';
  END IF;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'report_start_review', 'report', p_report_id,
    jsonb_build_object(
      'type', 'report',
      'id', p_report_id,
      'reported_target_type', v_report_target_type,
      'reported_target_id', v_report_target_id
    ),
    'Started report review'
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_finish_report(
  p_report_id bigint,
  p_resolution varchar,
  p_note varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_current_status varchar(20);
  v_current_handler_id bigint;
  v_report_target_type varchar(20);
  v_report_target_id bigint;
  v_action varchar(40);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF p_resolution NOT IN ('resolved', 'rejected') THEN
    RAISE EXCEPTION 'Resolution must be resolved or rejected.' USING ERRCODE = '23514';
  END IF;
  IF char_length(btrim(COALESCE(p_note, ''))) = 0 THEN
    RAISE EXCEPTION 'Resolution note is required.' USING ERRCODE = '23514';
  END IF;

  SELECT r.status, r.handled_by_user_id, r.target_type, r.target_entity_id
  INTO v_current_status, v_current_handler_id, v_report_target_type, v_report_target_id
  FROM public.reports r
  WHERE r.id = p_report_id
  FOR UPDATE;

  IF NOT FOUND OR v_current_status NOT IN ('pending', 'in_review') THEN
    RAISE EXCEPTION 'Open report % was not found.', p_report_id USING ERRCODE = 'P0002';
  END IF;

  IF v_current_status = 'in_review'
     AND v_current_handler_id IS DISTINCT FROM v_actor_user_id
     AND v_actor_role <> 'admin' THEN
    RAISE EXCEPTION 'Moderator cannot finish a report assigned to another staff member.'
      USING ERRCODE = '42501';
  END IF;

  UPDATE public.reports
  SET status = p_resolution,
      handled_by_user_id = v_actor_user_id,
      handled_by_username = v_actor_username,
      handled_by_role = v_actor_role,
      handled_at = CURRENT_TIMESTAMP,
      resolution_note = p_note
  WHERE id = p_report_id;

  v_action := CASE WHEN p_resolution = 'resolved' THEN 'report_resolve' ELSE 'report_reject' END;
  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    v_action, 'report', p_report_id,
    jsonb_build_object(
      'type', 'report',
      'id', p_report_id,
      'reported_target_type', v_report_target_type,
      'reported_target_id', v_report_target_id,
      'previous_status', v_current_status
    ),
    p_note
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_suspend_user(
  p_target_user_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_target_role varchar(20);
  v_target_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_target_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id
  FOR UPDATE;

  IF NOT FOUND OR v_target_status <> 'active' THEN
    RAISE EXCEPTION 'Active target user % was not found.', p_target_user_id USING ERRCODE = 'P0002';
  END IF;

  -- Validate staff-target rules only after the target row is locked so the target role
  -- cannot change between authorization and mutation.
  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  UPDATE public.app_users SET account_status = 'suspended' WHERE id = p_target_user_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'user_suspend', 'user', p_target_user_id,
    jsonb_build_object(
      'type', 'user', 'id', p_target_user_id, 'username', v_target_username,
      'role', v_target_role, 'account_status', v_target_status
    ),
    p_reason
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_restore_user(
  p_target_user_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_target_role varchar(20);
  v_target_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_target_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id
  FOR UPDATE;

  IF NOT FOUND OR v_target_status <> 'suspended' THEN
    RAISE EXCEPTION 'Suspended target user % was not found.', p_target_user_id USING ERRCODE = 'P0002';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  UPDATE public.app_users SET account_status = 'active' WHERE id = p_target_user_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'user_restore', 'user', p_target_user_id,
    jsonb_build_object(
      'type', 'user', 'id', p_target_user_id, 'username', v_target_username,
      'role', v_target_role, 'account_status', v_target_status
    ),
    p_reason
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_withdraw_user(
  p_target_user_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_target_role varchar(20);
  v_target_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(true) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_target_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id
  FOR UPDATE;

  IF NOT FOUND OR v_target_status = 'withdrawn' THEN
    RAISE EXCEPTION 'Target user % was not found or already withdrawn.', p_target_user_id USING ERRCODE = 'P0002';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  UPDATE public.app_users SET account_status = 'withdrawn' WHERE id = p_target_user_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'user_withdraw', 'user', p_target_user_id,
    jsonb_build_object(
      'type', 'user', 'id', p_target_user_id, 'username', v_target_username,
      'role', v_target_role, 'account_status', v_target_status
    ),
    p_reason
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_change_user_role(
  p_target_user_id bigint,
  p_new_role varchar,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_old_role varchar(20);
  v_target_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(true) s;

  IF p_new_role NOT IN ('user', 'moderator', 'admin') THEN
    RAISE EXCEPTION 'Invalid role: %', p_new_role USING ERRCODE = '23514';
  END IF;
  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_old_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target user % does not exist.', p_target_user_id USING ERRCODE = '23503';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  IF v_old_role = p_new_role THEN
    RAISE EXCEPTION 'Target user already has role %.', p_new_role USING ERRCODE = '23514';
  END IF;

  UPDATE public.app_users SET role = p_new_role WHERE id = p_target_user_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason, metadata
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'user_role_change', 'user', p_target_user_id,
    jsonb_build_object(
      'type', 'user', 'id', p_target_user_id, 'username', v_target_username,
      'role', v_old_role, 'account_status', v_target_status
    ),
    p_reason,
    jsonb_build_object('old_role', v_old_role, 'new_role', p_new_role)
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_hide_post(
  p_post_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_title varchar(150);
  v_author_id bigint;
  v_main_region_id bigint;
  v_content_status varchar(20);
  v_moderation_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT p.title, p.author_id, p.main_region_id, p.status, p.moderation_status
    INTO v_title, v_author_id, v_main_region_id, v_content_status, v_moderation_status
  FROM public.posts p
  WHERE p.id = p_post_id
  FOR UPDATE;

  IF NOT FOUND OR v_moderation_status <> 'visible' THEN
    RAISE EXCEPTION 'Moderation-visible post % was not found.', p_post_id
      USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.posts
  SET moderation_status = 'hidden'
  WHERE id = p_post_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason, metadata
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'post_hide', 'post', p_post_id,
    jsonb_build_object(
      'type', 'post', 'id', p_post_id, 'title', v_title,
      'author_id', v_author_id, 'main_region_id', v_main_region_id,
      'content_status', v_content_status, 'moderation_status', v_moderation_status
    ),
    p_reason,
    jsonb_build_object('content_status', v_content_status)
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_restore_post(
  p_post_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_title varchar(150);
  v_author_id bigint;
  v_main_region_id bigint;
  v_content_status varchar(20);
  v_moderation_status varchar(20);
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT p.title, p.author_id, p.main_region_id, p.status, p.moderation_status
    INTO v_title, v_author_id, v_main_region_id, v_content_status, v_moderation_status
  FROM public.posts p
  WHERE p.id = p_post_id
  FOR UPDATE;

  IF NOT FOUND OR v_moderation_status <> 'hidden' THEN
    RAISE EXCEPTION 'Moderation-hidden post % was not found.', p_post_id
      USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.posts
  SET moderation_status = 'visible'
  WHERE id = p_post_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason, metadata
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'post_restore', 'post', p_post_id,
    jsonb_build_object(
      'type', 'post', 'id', p_post_id, 'title', v_title,
      'author_id', v_author_id, 'main_region_id', v_main_region_id,
      'content_status', v_content_status, 'moderation_status', v_moderation_status
    ),
    p_reason,
    jsonb_build_object('content_status', v_content_status)
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_comment(
  p_comment_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_post_id bigint;
  v_author_id bigint;
  v_user_deleted_at timestamptz;
  v_moderation_deleted_at timestamptz;
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT c.post_id, c.author_id, c.deleted_at, c.moderation_deleted_at
    INTO v_post_id, v_author_id, v_user_deleted_at, v_moderation_deleted_at
  FROM public.comments c
  WHERE c.id = p_comment_id
  FOR UPDATE;

  IF NOT FOUND OR v_moderation_deleted_at IS NOT NULL THEN
    RAISE EXCEPTION 'Comment % was not found or is already moderation-deleted.', p_comment_id
      USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.comments
  SET moderation_deleted_at = CURRENT_TIMESTAMP
  WHERE id = p_comment_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'comment_delete', 'comment', p_comment_id,
    jsonb_build_object(
      'type', 'comment', 'id', p_comment_id,
      'post_id', v_post_id, 'author_id', v_author_id,
      'user_deleted_at', v_user_deleted_at,
      'moderation_deleted_at', v_moderation_deleted_at
    ),
    p_reason
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_restore_comment(
  p_comment_id bigint,
  p_reason varchar
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_post_id bigint;
  v_author_id bigint;
  v_user_deleted_at timestamptz;
  v_moderation_deleted_at timestamptz;
BEGIN
  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  SELECT c.post_id, c.author_id, c.deleted_at, c.moderation_deleted_at
    INTO v_post_id, v_author_id, v_user_deleted_at, v_moderation_deleted_at
  FROM public.comments c
  WHERE c.id = p_comment_id
  FOR UPDATE;

  IF NOT FOUND OR v_moderation_deleted_at IS NULL THEN
    RAISE EXCEPTION 'Moderation-deleted comment % was not found.', p_comment_id
      USING ERRCODE = 'P0002';
  END IF;

  UPDATE public.comments
  SET moderation_deleted_at = NULL
  WHERE id = p_comment_id;

  INSERT INTO public.admin_actions (
    actor_user_id, actor_username, actor_role,
    action_type, target_type, target_entity_id, target_snapshot, reason
  ) VALUES (
    v_actor_user_id, v_actor_username, v_actor_role,
    'comment_restore', 'comment', p_comment_id,
    jsonb_build_object(
      'type', 'comment', 'id', p_comment_id,
      'post_id', v_post_id, 'author_id', v_author_id,
      'user_deleted_at', v_user_deleted_at,
      'moderation_deleted_at', v_moderation_deleted_at
    ),
    p_reason
  );
END;
$$;

-- A pure view-count increment is not a content edit and must not move posts.updated_at.
CREATE OR REPLACE FUNCTION public.set_post_updated_at_except_view_count()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
  IF (to_jsonb(NEW) - 'view_count' - 'updated_at')
     = (to_jsonb(OLD) - 'view_count' - 'updated_at') THEN
    NEW.updated_at := OLD.updated_at;
  ELSE
    NEW.updated_at := CURRENT_TIMESTAMP;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS posts_set_updated_at ON public.posts;
CREATE TRIGGER posts_set_updated_at
BEFORE UPDATE ON public.posts
FOR EACH ROW EXECUTE FUNCTION public.set_post_updated_at_except_view_count();

-- View counts are not a normal editable post field. This function performs a single
-- atomic increment only for a currently published and moderation-visible post.
CREATE OR REPLACE FUNCTION public.increment_post_view(p_post_id bigint)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_raw text;
  v_user_id bigint;
  v_user_status varchar(20);
  v_view_count bigint;
BEGIN
  v_raw := current_setting('jc.current_user_id', true);

  IF v_raw IS NOT NULL AND btrim(v_raw) <> '' THEN
    IF char_length(v_raw) > 19 OR v_raw !~ '^[0-9]+$' THEN
      RAISE EXCEPTION 'Authenticated request user context is invalid.'
        USING ERRCODE = '42501';
    END IF;

    BEGIN
      v_user_id := v_raw::bigint;
    EXCEPTION WHEN numeric_value_out_of_range THEN
      RAISE EXCEPTION 'Authenticated request user context is outside bigint range.'
        USING ERRCODE = '42501';
    END;

    SELECT u.account_status
      INTO v_user_status
    FROM public.app_users u
    WHERE u.id = v_user_id;

    IF NOT FOUND OR v_user_status <> 'active' THEN
      RAISE EXCEPTION 'Authenticated request user is missing or inactive.'
        USING ERRCODE = '42501';
    END IF;
  END IF;

  UPDATE public.posts
  SET view_count = view_count + 1
  WHERE id = p_post_id
    AND public.can_user_view_post(v_user_id, p_post_id)
  RETURNING view_count INTO v_view_count;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Accessible visible published post % was not found.', p_post_id
      USING ERRCODE = 'P0002';
  END IF;

  RETURN v_view_count;
END;
$$;

-- Security-definer routines execute as this narrow, non-login owner rather than as the
-- migration owner or a superuser.
ALTER FUNCTION public.validate_report_live_target_on_insert() OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_report_evidence_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.can_user_view_post(bigint, bigint) OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_hidden_post_content_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_moderated_comment_content_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_hidden_post_child_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.current_request_user_id() OWNER TO jc_security_owner;
ALTER FUNCTION public.require_active_user() OWNER TO jc_security_owner;
ALTER FUNCTION public.require_staff_actor(boolean) OWNER TO jc_security_owner;
ALTER FUNCTION public.assert_staff_target_allowed(bigint, varchar, bigint) OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_admin_action_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.submit_report(varchar, bigint, varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_start_report_review(bigint) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_finish_report(bigint, varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_suspend_user(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_restore_user(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_withdraw_user(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_change_user_role(bigint, varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_hide_post(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_restore_post(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_delete_comment(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_restore_comment(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.increment_post_view(bigint) OWNER TO jc_security_owner;
ALTER FUNCTION public.set_post_updated_at_except_view_count() OWNER TO jc_security_owner;

-- Internal helpers are not runtime entry points.
REVOKE EXECUTE ON FUNCTION public.validate_report_live_target_on_insert()
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.prevent_report_evidence_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.can_user_view_post(bigint, bigint)
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.prevent_hidden_post_content_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.prevent_moderated_comment_content_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.prevent_hidden_post_child_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.current_request_user_id() FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.require_active_user() FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.require_staff_actor(boolean) FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.assert_staff_target_allowed(bigint, varchar, bigint)
FROM PUBLIC, jc_app, jc_auth, jc_admin;
REVOKE EXECUTE ON FUNCTION public.prevent_admin_action_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin;

-- Controlled runtime entry points.
GRANT EXECUTE ON FUNCTION public.submit_report(varchar, bigint, varchar, varchar) TO jc_app;
GRANT EXECUTE ON FUNCTION public.increment_post_view(bigint) TO jc_app;
GRANT EXECUTE ON FUNCTION public.admin_start_report_review(bigint) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_finish_report(bigint, varchar, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_suspend_user(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_restore_user(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_withdraw_user(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_change_user_role(bigint, varchar, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_hide_post(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_restore_post(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_delete_comment(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_restore_comment(bigint, varchar) TO jc_admin;

GRANT EXECUTE ON FUNCTION public.get_region_descendants(bigint) TO jc_app, jc_admin;
GRANT EXECUTE ON FUNCTION public.is_region_ancestor_or_same(bigint, bigint) TO jc_app, jc_admin;

-- The deferred integrity trigger calls this helper under the application role after
-- publishing or changing post places. It is read-only and intentionally executable by
-- jc_app so the write path cannot fail after PUBLIC function privileges are revoked.
GRANT EXECUTE ON FUNCTION public.assert_published_post_integrity(bigint) TO jc_app;

-- Purging is an operations task, not an application or admin endpoint.
REVOKE EXECUTE ON FUNCTION public.purge_expired_deleted_posts() FROM jc_app, jc_auth, jc_admin;

-- Newly created functions receive PUBLIC EXECUTE by default, so close the window again.
-- Explicit role grants above remain in effect.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;

-- Remove temporary ownership-transfer capabilities before commit.
REVOKE CREATE ON SCHEMA public FROM jc_security_owner;
REVOKE jc_security_owner FROM CURRENT_USER;

COMMIT;
