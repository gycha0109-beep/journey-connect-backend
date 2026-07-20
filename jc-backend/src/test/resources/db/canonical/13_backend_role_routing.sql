-- Journey Connect DB v2.1 - Backend role-routing privilege convergence
-- Target: PostgreSQL 15+
-- Prerequisite: 05_security_roles.sql, 08_recommendation_security_roles.sql,
--               11_backend_runtime_security_roles.sql
--
-- The application connects with one externally managed login role and executes
-- SET LOCAL ROLE at the transaction boundary. The login role must be a member of
-- jc_app, jc_auth and jc_recommendation, preferably with NOINHERIT. This migration
-- does not create a password-bearing login role.

BEGIN;

-- Authentication responses include the user's own public profile fields. They remain
-- unavailable to jc_app as credential-bearing columns are isolated in the auth mapping.
GRANT SELECT (display_name, profile_image_url, bio, created_at, updated_at)
ON public.app_users TO jc_auth;
GRANT UPDATE (display_name, profile_image_url, bio)
ON public.app_users TO jc_auth;

-- Reassert the credential boundary explicitly for convergent reruns.
REVOKE SELECT (email, password_hash, role) ON public.app_users FROM jc_app;
REVOKE UPDATE (email, password_hash, role, account_status) ON public.app_users FROM jc_app;
REVOKE SELECT (password_hash) ON public.app_users FROM jc_admin, jc_recommendation;

-- Recommendation and normal application roles must never access refresh-token material.
REVOKE ALL ON public.refresh_tokens FROM jc_app, jc_admin, jc_recommendation;

COMMIT;
