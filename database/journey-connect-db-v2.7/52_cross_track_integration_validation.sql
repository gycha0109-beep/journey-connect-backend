-- Journey Connect DB v2.7 - DP-7 rollback-only PostgreSQL 15/18 integration validation
-- The non-numbered include files are test fixture fragments, not migrations.
-- Required validation markers: NEW / DUPLICATE / CONFLICT / exactly one NEW / permissions / ROLLBACK;
\! cat verification/dp7/sql/52_cross_track_integration_validation_part1.inc verification/dp7/sql/52_cross_track_integration_validation_part2.inc verification/dp7/sql/52_cross_track_integration_validation_part3.inc verification/dp7/sql/52_cross_track_integration_validation_part4.inc > /tmp/dp7-52-validation.sql
\i /tmp/dp7-52-validation.sql
