-- Journey Connect DB v2.7 - DP-6 rollback-only PostgreSQL 15/18 validation
-- The non-numbered include files are test fixture fragments, not migrations.
-- Required validation markers:
-- valid FULL validation NEW failed
-- same validation DUPLICATE failed
-- quality verdict CONFLICT failed
-- zero denominator PASS unexpectedly succeeded
-- quality verdict UPDATE unexpectedly succeeded
-- quality check DELETE unexpectedly succeeded
-- quality function owner role is unsafe
-- quality least privilege or PUBLIC denial failed
-- ROLLBACK;
\ir ../../verification/dp6/sql/47_data_quality_validation_part1.inc
\ir ../../verification/dp6/sql/47_data_quality_validation_part2.inc
\ir ../../verification/dp6/sql/47_data_quality_validation_part3.inc
\ir ../../verification/dp6/sql/47_data_quality_validation_part4.inc
