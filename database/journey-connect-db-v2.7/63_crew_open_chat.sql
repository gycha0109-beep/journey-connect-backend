-- Journey Connect DB v2.7 - PF6 Crew open chat access
-- Target: PostgreSQL 15+
-- Prerequisite: 62_crew_recommendation_exposure_smoke_test.sql

BEGIN;

ALTER TABLE public.crews
  ADD COLUMN open_chat_url varchar(500);

GRANT UPDATE (open_chat_url) ON public.crews TO jc_app;

-- PF6 protects the member-only URL from the recommendation runtime while
-- preserving the exact Crew facts used by CrewRecommendationCandidateSource.
REVOKE SELECT ON public.crews FROM jc_recommendation;
GRANT SELECT (
  id,
  owner_id,
  region_id,
  travel_date,
  capacity,
  recruiting,
  created_at
) ON public.crews TO jc_recommendation;

COMMIT;
