-- Journey Connect DB v2.3 - Recommendation exploration partition correction
-- Target: PostgreSQL 15+
-- Prerequisite: 01-16 canonical SQL
--
-- Exploration candidates are terminal scoring outcomes promoted into the final
-- ranked list. Therefore input partitions into final-ranked plus terminal, while
-- final-ranked may be greater than the scored candidate count.

BEGIN;

ALTER TABLE public.recommendation_run
  DROP CONSTRAINT recommendation_run_count_partition_check,
  DROP CONSTRAINT recommendation_run_ranked_count_check;

ALTER TABLE public.recommendation_run
  ADD CONSTRAINT recommendation_run_count_partition_check
    CHECK (input_count = final_ranked_candidate_count + terminal_candidate_count),
  ADD CONSTRAINT recommendation_run_ranked_count_check
    CHECK (
      final_ranked_candidate_count >= scored_candidate_count
      AND final_ranked_candidate_count <= input_count
    );

COMMIT;
