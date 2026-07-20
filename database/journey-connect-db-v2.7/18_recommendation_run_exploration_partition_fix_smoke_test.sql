-- Journey Connect DB v2.3 - Recommendation exploration partition smoke test
-- Metadata-only assertions; no data is retained.

BEGIN;

DO $$
DECLARE
  v_count_partition text;
  v_ranked_partition text;
BEGIN
  SELECT pg_get_constraintdef(oid)
    INTO v_count_partition
  FROM pg_constraint
  WHERE conrelid = 'public.recommendation_run'::regclass
    AND conname = 'recommendation_run_count_partition_check';

  SELECT pg_get_constraintdef(oid)
    INTO v_ranked_partition
  FROM pg_constraint
  WHERE conrelid = 'public.recommendation_run'::regclass
    AND conname = 'recommendation_run_ranked_count_check';

  IF v_count_partition IS NULL
     OR position('input_count = (final_ranked_candidate_count + terminal_candidate_count)'
                 IN v_count_partition) = 0 THEN
    RAISE EXCEPTION 'Recommendation input partition must use final-ranked plus terminal counts: %',
      v_count_partition;
  END IF;

  IF v_ranked_partition IS NULL
     OR position('final_ranked_candidate_count >= scored_candidate_count'
                 IN v_ranked_partition) = 0
     OR position('final_ranked_candidate_count <= input_count'
                 IN v_ranked_partition) = 0 THEN
    RAISE EXCEPTION 'Recommendation ranked-count exploration bounds are invalid: %',
      v_ranked_partition;
  END IF;
END;
$$;

ROLLBACK;
