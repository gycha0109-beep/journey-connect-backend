-- SR-6F-D minimal internal dependency for the SECURITY DEFINER projection writer.
-- Run after 06_search_ctr_projection_writer.sql and before its smoke test.
BEGIN;

GRANT EXECUTE ON FUNCTION public.evaluate_search_ctr_v1(
  timestamptz, timestamptz, varchar
) TO jc_security_owner;

DO $$
BEGIN
  IF NOT has_function_privilege(
       'jc_security_owner',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR NOT has_function_privilege(
       'jc_reliability',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR has_function_privilege(
       'jc_app',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR has_function_privilege(
       'jc_auth',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR has_function_privilege(
       'jc_admin',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE')
     OR has_function_privilege(
       'jc_recommendation',
       'public.evaluate_search_ctr_v1(timestamp with time zone,timestamp with time zone,character varying)',
       'EXECUTE') THEN
    RAISE EXCEPTION 'Search CTR writer owner dependency privilege contract failed';
  END IF;
END;
$$;

COMMIT;
