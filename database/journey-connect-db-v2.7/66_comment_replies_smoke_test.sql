-- Journey Connect DB v2.7 - PF7 Comment replies smoke test
-- Target: PostgreSQL 15+
-- Prerequisite: 65_comment_replies.sql
-- Run as a PostgreSQL superuser because this script uses SET ROLE.

BEGIN;

DO $$
DECLARE
  v_type text;
  v_nullable text;
BEGIN
  SELECT data_type, is_nullable
    INTO v_type, v_nullable
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'comments'
    AND column_name = 'parent_comment_id';

  IF NOT FOUND OR v_type <> 'bigint' OR v_nullable <> 'YES' THEN
    RAISE EXCEPTION 'comments.parent_comment_id must be nullable BIGINT.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conrelid = 'public.comments'::regclass
      AND c.confrelid = 'public.comments'::regclass
      AND c.contype = 'f'
      AND c.conname = 'comments_parent_comment_fk'
  ) THEN
    RAISE EXCEPTION 'comments.parent_comment_id self foreign key is missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger t
    WHERE t.tgrelid = 'public.comments'::regclass
      AND t.tgname = 'comments_enforce_reply_structure'
      AND NOT t.tgisinternal
  ) THEN
    RAISE EXCEPTION 'PF7 comment reply structural trigger is missing.';
  END IF;

  IF NOT has_column_privilege(
      'jc_app', 'public.comments', 'parent_comment_id', 'INSERT'
  ) THEN
    RAISE EXCEPTION 'jc_app must be able to insert parent_comment_id.';
  END IF;
  IF has_column_privilege(
      'jc_app', 'public.comments', 'parent_comment_id', 'UPDATE'
  ) THEN
    RAISE EXCEPTION 'jc_app must not mutate parent_comment_id after creation.';
  END IF;
  IF has_table_privilege('jc_app', 'public.comments', 'DELETE') THEN
    RAISE EXCEPTION 'jc_app must not physically delete comments.';
  END IF;
  IF has_column_privilege('jc_app', 'public.comments', 'moderation_deleted_at', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must not update comment moderation state.';
  END IF;
  IF has_column_privilege('jc_auth', 'public.comments', 'parent_comment_id', 'INSERT')
     OR has_column_privilege('jc_admin', 'public.comments', 'parent_comment_id', 'INSERT')
     OR has_column_privilege('jc_recommendation', 'public.comments', 'parent_comment_id', 'INSERT') THEN
    RAISE EXCEPTION 'PF7 parent write authority leaked outside jc_app.';
  END IF;

  IF to_regclass('public.comment_replies') IS NOT NULL
     OR to_regclass('public.comment_reply_recommendation') IS NOT NULL
     OR to_regclass('public.comment_reply_search') IS NOT NULL
     OR to_regclass('public.comment_reply_exposure') IS NOT NULL THEN
    RAISE EXCEPTION 'PF7 must not create a second reply/recommendation/search/exposure table.';
  END IF;
END;
$$;

INSERT INTO public.app_users (email, password_hash, username, display_name)
VALUES
  ('pf7-author@example.test', 'hash', 'pf7_author', 'PF7 Author'),
  ('pf7-replier@example.test', 'hash', 'pf7_replier', 'PF7 Replier');

INSERT INTO public.posts (author_id, title, content, status)
SELECT u.id, 'PF7 post A', 'PF7 structural post A', 'draft'
FROM public.app_users u
WHERE u.username = 'pf7_author';

INSERT INTO public.posts (author_id, title, content, status)
SELECT u.id, 'PF7 post B', 'PF7 structural post B', 'draft'
FROM public.app_users u
WHERE u.username = 'pf7_author';

SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'pf7_replier'),
  true
);

INSERT INTO public.comments (post_id, author_id, content)
SELECT p.id, u.id, 'PF7 top-level parent'
FROM public.posts p
CROSS JOIN public.app_users u
WHERE p.title = 'PF7 post A'
  AND u.username = 'pf7_author';

INSERT INTO public.comments (post_id, author_id, content)
SELECT p.id, u.id, 'PF7 second top-level parent'
FROM public.posts p
CROSS JOIN public.app_users u
WHERE p.title = 'PF7 post A'
  AND u.username = 'pf7_author';

INSERT INTO public.comments (post_id, author_id, content, parent_comment_id)
SELECT p.id, u.id, 'PF7 valid reply', parent.id
FROM public.posts p
CROSS JOIN public.app_users u
CROSS JOIN public.comments parent
WHERE p.title = 'PF7 post A'
  AND u.username = 'pf7_replier'
  AND parent.content = 'PF7 top-level parent';

DO $$
BEGIN
  BEGIN
    INSERT INTO public.comments (post_id, author_id, content, parent_comment_id)
    SELECT p.id, u.id, 'PF7 cross-post invalid reply', parent.id
    FROM public.posts p
    CROSS JOIN public.app_users u
    CROSS JOIN public.comments parent
    WHERE p.title = 'PF7 post B'
      AND u.username = 'pf7_replier'
      AND parent.content = 'PF7 top-level parent';
    RAISE EXCEPTION 'Cross-post comment reply unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.comments (post_id, author_id, content, parent_comment_id)
    SELECT p.id, u.id, 'PF7 depth-two invalid reply', parent.id
    FROM public.posts p
    CROSS JOIN public.app_users u
    CROSS JOIN public.comments parent
    WHERE p.title = 'PF7 post A'
      AND u.username = 'pf7_replier'
      AND parent.content = 'PF7 valid reply';
    RAISE EXCEPTION 'Reply-to-reply unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END;
$$;

RESET ROLE;

DO $$
DECLARE
  v_parent_id bigint;
  v_reply_parent_id bigint;
BEGIN
  SELECT id INTO v_parent_id
  FROM public.comments
  WHERE content = 'PF7 top-level parent';

  SELECT parent_comment_id INTO v_reply_parent_id
  FROM public.comments
  WHERE content = 'PF7 valid reply';

  IF v_parent_id IS NULL OR v_reply_parent_id IS DISTINCT FROM v_parent_id THEN
    RAISE EXCEPTION 'Valid PF7 reply did not retain its parent_comment_id.';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.comments
    WHERE content IN ('PF7 top-level parent', 'PF7 second top-level parent')
      AND parent_comment_id IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Top-level comments must keep NULL parent_comment_id.';
  END IF;

  BEGIN
    UPDATE public.comments
    SET parent_comment_id = (
      SELECT id FROM public.comments WHERE content = 'PF7 second top-level parent'
    )
    WHERE content = 'PF7 top-level parent';
    RAISE EXCEPTION 'A parent with existing replies was allowed to become a reply.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    UPDATE public.comments
    SET parent_comment_id = id
    WHERE content = 'PF7 second top-level parent';
    RAISE EXCEPTION 'A comment was allowed to reply to itself.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END;
$$;

ROLLBACK;
