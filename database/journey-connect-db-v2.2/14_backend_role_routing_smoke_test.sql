-- Journey Connect DB v2.1 - Backend role-routing privilege smoke test
-- Run after 13_backend_role_routing.sql.

BEGIN;

DO $$
BEGIN
  IF NOT has_column_privilege('jc_app', 'public.app_users', 'display_name', 'SELECT') THEN
    RAISE EXCEPTION 'jc_app must read public profile fields.';
  END IF;
  IF has_column_privilege('jc_app', 'public.app_users', 'email', 'SELECT')
     OR has_column_privilege('jc_app', 'public.app_users', 'password_hash', 'SELECT')
     OR has_column_privilege('jc_app', 'public.app_users', 'role', 'SELECT') THEN
    RAISE EXCEPTION 'jc_app must not read credential or authority columns.';
  END IF;

  IF NOT has_column_privilege('jc_auth', 'public.app_users', 'password_hash', 'SELECT')
     OR NOT has_column_privilege('jc_auth', 'public.app_users', 'display_name', 'SELECT')
     OR NOT has_column_privilege('jc_auth', 'public.app_users', 'bio', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_auth account/profile privileges are incomplete.';
  END IF;
  IF has_column_privilege('jc_auth', 'public.app_users', 'role', 'UPDATE')
     OR has_column_privilege('jc_auth', 'public.app_users', 'account_status', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_auth must not mutate authority or account status.';
  END IF;

  IF has_table_privilege('jc_app', 'public.refresh_tokens', 'SELECT')
     OR has_table_privilege('jc_recommendation', 'public.refresh_tokens', 'SELECT') THEN
    RAISE EXCEPTION 'Non-auth roles must not access refresh tokens.';
  END IF;
  IF NOT has_table_privilege('jc_auth', 'public.refresh_tokens', 'SELECT')
     OR NOT has_table_privilege('jc_auth', 'public.refresh_tokens', 'INSERT')
     OR NOT has_table_privilege('jc_auth', 'public.refresh_tokens', 'UPDATE')
     OR NOT has_table_privilege('jc_auth', 'public.refresh_tokens', 'DELETE') THEN
    RAISE EXCEPTION 'jc_auth refresh-token privileges are incomplete.';
  END IF;
END;
$$;

ROLLBACK;
