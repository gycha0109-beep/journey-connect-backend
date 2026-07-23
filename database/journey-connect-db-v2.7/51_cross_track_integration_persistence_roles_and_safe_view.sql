-- Journey Connect DB v2.7 extension - DP-7 atomic persistence, roles and aggregate-safe view
-- The non-numbered include files are implementation fragments, not migrations.
-- SECURITY DEFINER / pg_advisory_xact_lock / NEW / DUPLICATE / CONFLICT / safe aggregate view
\! cat verification/dp7/sql/51_cross_track_integration_persistence_roles_and_safe_view_part1.inc verification/dp7/sql/51_cross_track_integration_persistence_roles_and_safe_view_part2.inc verification/dp7/sql/51_cross_track_integration_persistence_roles_and_safe_view_part3.inc verification/dp7/sql/51_cross_track_integration_persistence_roles_and_safe_view_part4.inc > /tmp/dp7-51-persistence.sql
\i /tmp/dp7-51-persistence.sql
GRANT EXECUTE ON FUNCTION public.digest(bytea,text),public.data_event_canonical_json_v1(jsonb) TO jc_data_integration_function_owner;
