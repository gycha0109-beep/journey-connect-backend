-- Journey Connect DB v2.0 - Backend runtime authorization extension
-- Target: PostgreSQL 15+
-- Prerequisite: 05_security_roles.sql and 10_backend_runtime.sql

BEGIN;

REVOKE ALL ON public.refresh_tokens, public.crews, public.crew_members
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.refresh_tokens TO jc_auth;
GRANT USAGE ON SEQUENCE public.refresh_tokens_id_seq TO jc_auth;

GRANT SELECT, INSERT ON public.crews TO jc_app;
GRANT UPDATE (region_id, title, description, travel_date, capacity, recruiting, approval_required)
ON public.crews TO jc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.crew_members TO jc_app;
GRANT USAGE ON SEQUENCE public.crews_id_seq, public.crew_members_id_seq TO jc_app;

GRANT SELECT ON public.crews, public.crew_members TO jc_admin;
GRANT SELECT ON public.crews, public.crew_members TO jc_recommendation;

-- Both the application read path and recommendation candidate projection use the
-- canonical visibility function. Grant only this stable predicate, not underlying
-- follow/private-content tables beyond their existing table privileges.
GRANT EXECUTE ON FUNCTION public.can_user_view_post(bigint, bigint)
TO jc_app, jc_recommendation;

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.crews, public.crew_members
FROM jc_auth, jc_admin, jc_recommendation;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.refresh_tokens
FROM jc_app, jc_admin, jc_recommendation;
REVOKE DELETE, TRUNCATE ON public.crews FROM jc_app;

ALTER FUNCTION public.validate_crew_member_owner_binding() OWNER TO jc_security_owner;
ALTER FUNCTION public.assert_crew_membership_integrity(bigint) OWNER TO jc_security_owner;
ALTER FUNCTION public.check_crew_integrity_from_crew() OWNER TO jc_security_owner;
ALTER FUNCTION public.check_crew_integrity_from_member() OWNER TO jc_security_owner;
REVOKE EXECUTE ON FUNCTION public.validate_crew_member_owner_binding(),
  public.assert_crew_membership_integrity(bigint),
  public.check_crew_integrity_from_crew(),
  public.check_crew_integrity_from_member()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;

COMMIT;
