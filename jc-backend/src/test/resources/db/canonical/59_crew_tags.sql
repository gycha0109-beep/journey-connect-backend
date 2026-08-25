-- Journey Connect DB v2.7 - CR-1 crew tag association
-- Target: PostgreSQL 15+
-- Prerequisite: canonical tags, crews and runtime roles

BEGIN;

CREATE TABLE public.crew_tags (
  crew_id bigint NOT NULL REFERENCES public.crews(id) ON DELETE CASCADE,
  tag_id bigint NOT NULL REFERENCES public.tags(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (crew_id, tag_id)
);

CREATE INDEX crew_tags_tag_id_idx ON public.crew_tags (tag_id, crew_id);

CREATE OR REPLACE FUNCTION public.enforce_crew_tag_limit()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_crew_id bigint;
BEGIN
  v_crew_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.crew_id ELSE NEW.crew_id END;
  PERFORM pg_advisory_xact_lock(hashtextextended('crew_tags:' || v_crew_id::text, 0));

  IF TG_OP <> 'DELETE'
     AND (SELECT count(*) FROM public.crew_tags ct WHERE ct.crew_id = v_crew_id) > 5 THEN
    RAISE EXCEPTION 'Crew % cannot have more than 5 tags.', v_crew_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER crew_tags_limit_guard
AFTER INSERT OR UPDATE OR DELETE ON public.crew_tags
DEFERRABLE INITIALLY IMMEDIATE
FOR EACH ROW EXECUTE FUNCTION public.enforce_crew_tag_limit();

REVOKE ALL ON public.crew_tags
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;

GRANT SELECT, INSERT, DELETE ON public.crew_tags TO jc_app;
GRANT SELECT ON public.crew_tags TO jc_admin, jc_recommendation;

REVOKE UPDATE, TRUNCATE ON public.crew_tags
FROM jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;
REVOKE INSERT, DELETE ON public.crew_tags
FROM jc_auth, jc_admin, jc_security_owner, jc_recommendation;

REVOKE EXECUTE ON FUNCTION public.enforce_crew_tag_limit()
FROM PUBLIC, jc_auth, jc_admin, jc_security_owner, jc_recommendation;
GRANT EXECUTE ON FUNCTION public.enforce_crew_tag_limit() TO jc_app;

COMMIT;
