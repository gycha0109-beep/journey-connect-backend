-- Journey Connect DB v2.7 - PF7 Comment replies
-- Target: PostgreSQL 15+
-- Prerequisite: 64_crew_open_chat_smoke_test.sql

BEGIN;

ALTER TABLE public.comments
  ADD COLUMN parent_comment_id bigint;

ALTER TABLE public.comments
  ADD CONSTRAINT comments_parent_comment_fk
  FOREIGN KEY (parent_comment_id)
  REFERENCES public.comments(id)
  ON DELETE RESTRICT;

CREATE INDEX comments_parent_comment_id_idx
ON public.comments (parent_comment_id)
WHERE parent_comment_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.enforce_comment_reply_structure()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_parent_post_id bigint;
  v_parent_parent_comment_id bigint;
BEGIN
  IF NEW.parent_comment_id IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.id IS NOT NULL AND NEW.parent_comment_id = NEW.id THEN
    RAISE EXCEPTION 'Comment % cannot reply to itself.', NEW.id
      USING ERRCODE = '23514';
  END IF;

  SELECT c.post_id, c.parent_comment_id
    INTO v_parent_post_id, v_parent_parent_comment_id
  FROM public.comments c
  WHERE c.id = NEW.parent_comment_id;

  IF NOT FOUND THEN
    RETURN NEW;
  END IF;

  IF v_parent_post_id IS DISTINCT FROM NEW.post_id THEN
    RAISE EXCEPTION 'Comment reply parent % belongs to a different post.', NEW.parent_comment_id
      USING ERRCODE = '23514';
  END IF;

  IF v_parent_parent_comment_id IS NOT NULL THEN
    RAISE EXCEPTION 'Comment reply parent % is not top-level.', NEW.parent_comment_id
      USING ERRCODE = '23514';
  END IF;

  IF NEW.id IS NOT NULL AND EXISTS (
    SELECT 1
    FROM public.comments child
    WHERE child.parent_comment_id = NEW.id
      AND child.id IS DISTINCT FROM NEW.id
  ) THEN
    RAISE EXCEPTION 'Comment % already has replies and must remain top-level.', NEW.id
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER comments_enforce_reply_structure
BEFORE INSERT OR UPDATE OF post_id, parent_comment_id ON public.comments
FOR EACH ROW EXECUTE FUNCTION public.enforce_comment_reply_structure();

-- SQL05 granted INSERT before parent_comment_id existed. Extend only the APP insert
-- capability required by PF7; parent linkage remains immutable after creation.
GRANT INSERT (parent_comment_id) ON public.comments TO jc_app;
REVOKE UPDATE (parent_comment_id) ON public.comments FROM jc_app;

REVOKE EXECUTE ON FUNCTION public.enforce_comment_reply_structure()
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_recommendation;

COMMIT;
