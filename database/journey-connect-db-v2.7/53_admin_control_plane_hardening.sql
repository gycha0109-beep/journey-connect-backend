-- Journey Connect DB v2.7 - ADM-3 admin control-plane lockout hardening
-- Target: PostgreSQL 15+
-- Prerequisite: 28_search_document_projection_smoke_test.sql already applied.
--
-- This forward-only migration serializes every runtime path that can remove an active
-- admin from the control plane. A transaction-scoped advisory lock coordinates across
-- backend instances. Actor and target rows are then locked in deterministic id order,
-- the actor is re-authorized from current database state, and at least one active admin
-- must remain before an active admin is suspended, withdrawn or demoted.

BEGIN;

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
  v_request_user_id bigint;
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_target_role varchar(20);
  v_target_status varchar(20);
  v_active_admin_count bigint;
BEGIN
  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  -- Shared with withdraw and role-change paths. The lock is transaction-scoped and
  -- therefore releases automatically on commit or rollback.
  PERFORM pg_catalog.pg_advisory_xact_lock(1245789, 3);
  v_request_user_id := public.current_request_user_id();

  -- Deterministic row ordering prevents actor/target inversion deadlocks. The actor
  -- remains locked after the authoritative recheck, so another privileged command
  -- cannot revoke that actor before this transaction commits.
  PERFORM 1
  FROM public.app_users u
  WHERE u.id IN (v_request_user_id, p_target_user_id)
  ORDER BY u.id
  FOR UPDATE;

  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(false) s;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_target_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id;

  IF NOT FOUND OR v_target_status <> 'active' THEN
    RAISE EXCEPTION 'Active target user % was not found.', p_target_user_id USING ERRCODE = 'P0002';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  IF v_target_role = 'admin' THEN
    SELECT count(*)
      INTO v_active_admin_count
    FROM public.app_users u
    WHERE u.role = 'admin'
      AND u.account_status = 'active';

    IF v_active_admin_count <= 1 THEN
      RAISE EXCEPTION 'At least one active admin account must remain.' USING ERRCODE = '23514';
    END IF;
  END IF;

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
  v_request_user_id bigint;
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_target_role varchar(20);
  v_target_status varchar(20);
  v_active_admin_count bigint;
BEGIN
  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(1245789, 3);
  v_request_user_id := public.current_request_user_id();

  PERFORM 1
  FROM public.app_users u
  WHERE u.id IN (v_request_user_id, p_target_user_id)
  ORDER BY u.id
  FOR UPDATE;

  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(true) s;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_target_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id;

  IF NOT FOUND OR v_target_status = 'withdrawn' THEN
    RAISE EXCEPTION 'Target user % was not found or already withdrawn.', p_target_user_id USING ERRCODE = 'P0002';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  IF v_target_role = 'admin' AND v_target_status = 'active' THEN
    SELECT count(*)
      INTO v_active_admin_count
    FROM public.app_users u
    WHERE u.role = 'admin'
      AND u.account_status = 'active';

    IF v_active_admin_count <= 1 THEN
      RAISE EXCEPTION 'At least one active admin account must remain.' USING ERRCODE = '23514';
    END IF;
  END IF;

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
  v_request_user_id bigint;
  v_actor_user_id bigint;
  v_actor_role varchar(20);
  v_actor_username varchar(30);
  v_target_username varchar(30);
  v_old_role varchar(20);
  v_target_status varchar(20);
  v_active_admin_count bigint;
BEGIN
  IF p_new_role NOT IN ('user', 'moderator', 'admin') THEN
    RAISE EXCEPTION 'Invalid role: %', p_new_role USING ERRCODE = '23514';
  END IF;
  IF char_length(btrim(COALESCE(p_reason, ''))) = 0 THEN
    RAISE EXCEPTION 'Reason is required.' USING ERRCODE = '23514';
  END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(1245789, 3);
  v_request_user_id := public.current_request_user_id();

  PERFORM 1
  FROM public.app_users u
  WHERE u.id IN (v_request_user_id, p_target_user_id)
  ORDER BY u.id
  FOR UPDATE;

  SELECT s.actor_user_id, s.actor_role, s.actor_username
    INTO v_actor_user_id, v_actor_role, v_actor_username
  FROM public.require_staff_actor(true) s;

  SELECT u.username, u.role, u.account_status
    INTO v_target_username, v_old_role, v_target_status
  FROM public.app_users u
  WHERE u.id = p_target_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target user % does not exist.', p_target_user_id USING ERRCODE = '23503';
  END IF;

  PERFORM public.assert_staff_target_allowed(v_actor_user_id, v_actor_role, p_target_user_id);

  IF v_old_role = p_new_role THEN
    RAISE EXCEPTION 'Target user already has role %.', p_new_role USING ERRCODE = '23514';
  END IF;

  IF v_old_role = 'admin' AND p_new_role <> 'admin' AND v_target_status = 'active' THEN
    SELECT count(*)
      INTO v_active_admin_count
    FROM public.app_users u
    WHERE u.role = 'admin'
      AND u.account_status = 'active';

    IF v_active_admin_count <= 1 THEN
      RAISE EXCEPTION 'At least one active admin account must remain.' USING ERRCODE = '23514';
    END IF;
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


-- ADM-3 command adapters convert same-state races into an explicit boolean no-op.
-- The exception block is a PostgreSQL subtransaction: an expected P0002 from the
-- legacy command is rolled back locally, allowing an authoritative state read
-- without leaving the outer application transaction aborted.
CREATE OR REPLACE FUNCTION public.admin_finish_report_command(
  p_report_id bigint,
  p_resolution varchar,
  p_note varchar
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_status varchar(20);
BEGIN
  BEGIN
    PERFORM public.admin_finish_report(p_report_id, p_resolution, p_note);
    RETURN true;
  EXCEPTION WHEN SQLSTATE 'P0002' THEN
    SELECT r.status INTO v_status FROM public.reports r WHERE r.id = p_report_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Admin target was not found.' USING ERRCODE = 'P0002';
    END IF;
    IF v_status = p_resolution THEN
      RETURN false;
    END IF;
    RAISE EXCEPTION 'Report state conflict.' USING ERRCODE = '23514';
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_hide_post_command(p_post_id bigint, p_reason varchar)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_state varchar(20);
BEGIN
  BEGIN
    PERFORM public.admin_hide_post(p_post_id, p_reason);
    RETURN true;
  EXCEPTION WHEN SQLSTATE 'P0002' THEN
    SELECT p.moderation_status INTO v_state FROM public.posts p WHERE p.id = p_post_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Admin target was not found.' USING ERRCODE = 'P0002';
    END IF;
    IF v_state = 'hidden' THEN
      RETURN false;
    END IF;
    RAISE EXCEPTION 'Post state conflict.' USING ERRCODE = '23514';
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_restore_post_command(p_post_id bigint, p_reason varchar)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_state varchar(20);
BEGIN
  BEGIN
    PERFORM public.admin_restore_post(p_post_id, p_reason);
    RETURN true;
  EXCEPTION WHEN SQLSTATE 'P0002' THEN
    SELECT p.moderation_status INTO v_state FROM public.posts p WHERE p.id = p_post_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Admin target was not found.' USING ERRCODE = 'P0002';
    END IF;
    IF v_state = 'visible' THEN
      RETURN false;
    END IF;
    RAISE EXCEPTION 'Post state conflict.' USING ERRCODE = '23514';
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_suspend_user_command(p_target_user_id bigint, p_reason varchar)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_state varchar(20);
BEGIN
  BEGIN
    PERFORM public.admin_suspend_user(p_target_user_id, p_reason);
    RETURN true;
  EXCEPTION WHEN SQLSTATE 'P0002' THEN
    SELECT u.account_status INTO v_state FROM public.app_users u WHERE u.id = p_target_user_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Admin target was not found.' USING ERRCODE = 'P0002';
    END IF;
    IF v_state = 'suspended' THEN
      RETURN false;
    END IF;
    RAISE EXCEPTION 'User state conflict.' USING ERRCODE = '23514';
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_restore_user_command(p_target_user_id bigint, p_reason varchar)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_state varchar(20);
BEGIN
  BEGIN
    PERFORM public.admin_restore_user(p_target_user_id, p_reason);
    RETURN true;
  EXCEPTION WHEN SQLSTATE 'P0002' THEN
    SELECT u.account_status INTO v_state FROM public.app_users u WHERE u.id = p_target_user_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'Admin target was not found.' USING ERRCODE = 'P0002';
    END IF;
    IF v_state = 'active' THEN
      RETURN false;
    END IF;
    RAISE EXCEPTION 'User state conflict.' USING ERRCODE = '23514';
  END;
END;
$$;

ALTER FUNCTION public.admin_suspend_user(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_withdraw_user(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_change_user_role(bigint, varchar, varchar) OWNER TO jc_security_owner;

REVOKE EXECUTE ON FUNCTION public.admin_suspend_user(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_withdraw_user(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_change_user_role(bigint, varchar, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
GRANT EXECUTE ON FUNCTION public.admin_suspend_user(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_withdraw_user(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_change_user_role(bigint, varchar, varchar) TO jc_admin;

ALTER FUNCTION public.admin_finish_report_command(bigint, varchar, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_hide_post_command(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_restore_post_command(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_suspend_user_command(bigint, varchar) OWNER TO jc_security_owner;
ALTER FUNCTION public.admin_restore_user_command(bigint, varchar) OWNER TO jc_security_owner;
REVOKE EXECUTE ON FUNCTION public.admin_finish_report_command(bigint, varchar, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_hide_post_command(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_restore_post_command(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_suspend_user_command(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
REVOKE EXECUTE ON FUNCTION public.admin_restore_user_command(bigint, varchar) FROM PUBLIC, jc_app, jc_auth, jc_recommendation;
GRANT EXECUTE ON FUNCTION public.admin_finish_report_command(bigint, varchar, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_hide_post_command(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_restore_post_command(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_suspend_user_command(bigint, varchar) TO jc_admin;
GRANT EXECUTE ON FUNCTION public.admin_restore_user_command(bigint, varchar) TO jc_admin;

COMMIT;
