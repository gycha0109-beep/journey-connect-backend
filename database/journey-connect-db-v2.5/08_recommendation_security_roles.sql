-- Journey Connect DB v1.9 - P0 recommendation database authorization
-- Target: PostgreSQL 15+
-- Prerequisite: 05_security_roles.sql and 07_recommendation_storage.sql
-- Run as PostgreSQL superuser, or a migration role with CREATEROLE and object ownership.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_recommendation') THEN
    CREATE ROLE jc_recommendation
      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
  END IF;
END;
$$;

DO $$
DECLARE
  v_unsafe_roles text;
BEGIN
  SELECT string_agg(rolname, ', ' ORDER BY rolname)
    INTO v_unsafe_roles
  FROM pg_roles
  WHERE rolname IN (
      'jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner', 'jc_recommendation'
    )
    AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls);

  IF v_unsafe_roles IS NOT NULL THEN
    RAISE EXCEPTION 'Unsafe pre-existing Journey Connect role attributes: %. Normalize or drop these roles as a PostgreSQL superuser, then rerun.',
      v_unsafe_roles;
  END IF;
END;
$$;

-- Recommendation execution is a separate backend trust boundary. It does not inherit
-- the shared content, authentication or administrator runtime roles, and vice versa.
REVOKE jc_app FROM jc_recommendation;
REVOKE jc_auth FROM jc_recommendation;
REVOKE jc_admin FROM jc_recommendation;
REVOKE jc_recommendation FROM jc_app, jc_auth, jc_admin, jc_security_owner;

DO $$
DECLARE
  v_unexpected_memberships text;
BEGIN
  SELECT string_agg(
           member_role.rolname || ' -> ' || granted_role.rolname,
           ', ' ORDER BY member_role.rolname, granted_role.rolname
         )
    INTO v_unexpected_memberships
  FROM pg_auth_members m
  JOIN pg_roles member_role ON member_role.oid = m.member
  JOIN pg_roles granted_role ON granted_role.oid = m.roleid
  WHERE member_role.rolname IN (
      'jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner', 'jc_recommendation'
    );

  IF v_unexpected_memberships IS NOT NULL THEN
    RAISE EXCEPTION 'Journey Connect security roles must not inherit unrelated roles: %',
      v_unexpected_memberships;
  END IF;
END;
$$;

GRANT jc_security_owner TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO jc_recommendation;
GRANT CREATE ON SCHEMA public TO jc_security_owner;

REVOKE ALL ON public.recommendation_snapshot,
  public.recommendation_run,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_exposure_event,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;

-- Candidate retrieval and run validation deliberately exclude authentication secrets.
GRANT SELECT (id, username, display_name, profile_image_url, bio,
              account_status, created_at, updated_at)
ON public.app_users TO jc_recommendation;

GRANT SELECT ON public.regions, public.places, public.tags, public.posts,
  public.post_images, public.post_places, public.post_tags, public.comments,
  public.post_likes, public.bookmarks, public.follows
TO jc_recommendation;

-- The recommendation service writes immutable history directly. UPDATE, DELETE and
-- TRUNCATE remain absent; append-only triggers provide a second database-side barrier.
GRANT SELECT, INSERT ON public.recommendation_snapshot,
  public.recommendation_run,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_exposure_event,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event
TO jc_recommendation;

-- Administrators may inspect recommendation evidence but cannot mutate it.
GRANT SELECT ON public.recommendation_snapshot,
  public.recommendation_run,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_exposure_event,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event
TO jc_admin;

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.recommendation_snapshot,
  public.recommendation_run,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_exposure_event,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event
FROM jc_app, jc_auth, jc_admin;

REVOKE UPDATE, DELETE, TRUNCATE ON public.recommendation_snapshot,
  public.recommendation_run,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_exposure_event,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event
FROM jc_recommendation;

-- recommendation_sha256_hex executes as this narrow owner. pgcrypto digest itself is
-- not exposed to runtime roles because 05_security_roles.sql revoked PUBLIC function use.
GRANT EXECUTE ON FUNCTION public.digest(bytea, text) TO jc_security_owner;

ALTER FUNCTION public.recommendation_sha256_hex(bytea) OWNER TO jc_security_owner;
ALTER FUNCTION public.recommendation_snapshot_sha256_hex(varchar, varchar, bytea)
OWNER TO jc_security_owner;
ALTER FUNCTION public.prevent_recommendation_append_only_mutation() OWNER TO jc_security_owner;
ALTER FUNCTION public.validate_recommendation_run_snapshot_bindings() OWNER TO jc_security_owner;
ALTER FUNCTION public.validate_recommendation_run_candidate_source() OWNER TO jc_security_owner;
ALTER FUNCTION public.assert_recommendation_run_integrity(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.check_recommendation_run_integrity_from_run() OWNER TO jc_security_owner;
ALTER FUNCTION public.check_recommendation_run_integrity_from_ranked_candidate() OWNER TO jc_security_owner;
ALTER FUNCTION public.check_recommendation_run_integrity_from_terminal_candidate() OWNER TO jc_security_owner;
ALTER FUNCTION public.validate_recommendation_exposure_event_binding() OWNER TO jc_security_owner;
ALTER FUNCTION public.validate_recommendation_exposure_candidate_binding() OWNER TO jc_security_owner;
ALTER FUNCTION public.assert_recommendation_exposure_event_integrity(varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.check_recommendation_exposure_integrity_from_event() OWNER TO jc_security_owner;
ALTER FUNCTION public.check_recommendation_exposure_integrity_from_candidate() OWNER TO jc_security_owner;
ALTER FUNCTION public.validate_recommendation_behavior_event_binding() OWNER TO jc_security_owner;

REVOKE EXECUTE ON FUNCTION public.recommendation_sha256_hex(bytea)
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.recommendation_snapshot_sha256_hex(varchar, varchar, bytea)
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.prevent_recommendation_append_only_mutation()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.validate_recommendation_run_snapshot_bindings()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.validate_recommendation_run_candidate_source()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.assert_recommendation_run_integrity(varchar)
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.check_recommendation_run_integrity_from_run()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.check_recommendation_run_integrity_from_ranked_candidate()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.check_recommendation_run_integrity_from_terminal_candidate()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.validate_recommendation_exposure_event_binding()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.validate_recommendation_exposure_candidate_binding()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.assert_recommendation_exposure_event_integrity(varchar)
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.check_recommendation_exposure_integrity_from_event()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.check_recommendation_exposure_integrity_from_candidate()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.validate_recommendation_behavior_event_binding()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;

-- These helpers are invoked by canonical-hash, CHECK and deferred-trigger paths while
-- inserts execute as jc_recommendation. No other internal function is a runtime entry point.
GRANT EXECUTE ON FUNCTION public.recommendation_sha256_hex(bytea) TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.recommendation_snapshot_sha256_hex(varchar, varchar, bytea)
TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.assert_recommendation_run_integrity(varchar)
TO jc_recommendation;
GRANT EXECUTE ON FUNCTION public.assert_recommendation_exposure_event_integrity(varchar)
TO jc_recommendation;

REVOKE CREATE ON SCHEMA public FROM jc_security_owner;
REVOKE jc_security_owner FROM CURRENT_USER;

COMMIT;
