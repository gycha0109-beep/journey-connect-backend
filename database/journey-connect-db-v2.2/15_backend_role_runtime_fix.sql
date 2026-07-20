-- Journey Connect DB v2.2 - Restricted-role runtime regression fixes
-- Target: PostgreSQL 15+
-- Prerequisite: 01-14 canonical SQL
--
-- Deferred crew constraint triggers execute during commit under the transaction's
-- current runtime role. Their internal reads therefore run through a narrowly owned,
-- non-login SECURITY DEFINER boundary while remaining unavailable as public APIs.

BEGIN;

GRANT SELECT ON public.crews, public.crew_members TO jc_security_owner;

ALTER FUNCTION public.validate_crew_member_owner_binding() SECURITY DEFINER;
ALTER FUNCTION public.validate_crew_member_owner_binding()
  SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION public.assert_crew_membership_integrity(bigint) SECURITY DEFINER;
ALTER FUNCTION public.assert_crew_membership_integrity(bigint)
  SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION public.check_crew_integrity_from_crew() SECURITY DEFINER;
ALTER FUNCTION public.check_crew_integrity_from_crew()
  SET search_path = pg_catalog, pg_temp;

ALTER FUNCTION public.check_crew_integrity_from_member() SECURITY DEFINER;
ALTER FUNCTION public.check_crew_integrity_from_member()
  SET search_path = pg_catalog, pg_temp;

REVOKE EXECUTE ON FUNCTION public.validate_crew_member_owner_binding(),
  public.assert_crew_membership_integrity(bigint),
  public.check_crew_integrity_from_crew(),
  public.check_crew_integrity_from_member()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;

COMMIT;
