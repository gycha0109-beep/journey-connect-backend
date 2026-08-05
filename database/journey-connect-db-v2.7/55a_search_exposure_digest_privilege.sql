-- SR-6C minimal pgcrypto privilege required by the authoritative payload hash CHECK.
BEGIN;

GRANT EXECUTE ON FUNCTION public.digest(bytea, text) TO jc_recommendation;

COMMIT;
