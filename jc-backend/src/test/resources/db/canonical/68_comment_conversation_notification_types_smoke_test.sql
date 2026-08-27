-- Journey Connect DB v2.7 - PF8 Comment conversation notification types smoke test
-- Target: PostgreSQL 15+
-- Prerequisite: 67_comment_conversation_notification_types.sql
-- Run as a PostgreSQL superuser because this script uses SET ROLE.

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conrelid = 'public.user_notifications'::regclass
      AND c.conname = 'user_notifications_type_check'
      AND c.contype = 'c'
  ) THEN
    RAISE EXCEPTION 'PF8 notification type check is missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conrelid = 'public.user_notifications'::regclass
      AND c.conname = 'user_notifications_target_type_check'
      AND c.contype = 'c'
  ) THEN
    RAISE EXCEPTION 'PF8 notification target pairing check is missing.';
  END IF;

  IF NOT has_table_privilege('jc_app', 'public.user_notifications', 'SELECT')
     OR NOT has_table_privilege('jc_app', 'public.user_notifications', 'INSERT')
     OR NOT has_column_privilege('jc_app', 'public.user_notifications', 'read_at', 'UPDATE') THEN
    RAISE EXCEPTION 'PF8 must preserve jc_app notification runtime authority.';
  END IF;

  IF has_column_privilege('jc_app', 'public.user_notifications', 'type', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.user_notifications', 'recipient_id', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'DELETE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'TRUNCATE') THEN
    RAISE EXCEPTION 'PF8 widened jc_app notification mutation privileges.';
  END IF;

  IF has_table_privilege('jc_auth', 'public.user_notifications', 'SELECT')
     OR has_table_privilege('jc_auth', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_admin', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_recommendation', 'public.user_notifications', 'SELECT')
     OR has_table_privilege('jc_recommendation', 'public.user_notifications', 'INSERT') THEN
    RAISE EXCEPTION 'PF8 leaked notification runtime authority outside jc_app.';
  END IF;

  IF NOT has_sequence_privilege('jc_app', 'public.user_notifications_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'PF8 must preserve jc_app notification identity sequence usage.';
  END IF;
END;
$$;

INSERT INTO public.app_users (email, password_hash, username, display_name)
VALUES
  ('pf8-recipient@example.test', 'hash', 'pf8_recipient', 'PF8 Recipient'),
  ('pf8-actor@example.test', 'hash', 'pf8_actor', 'PF8 Actor');

SET LOCAL ROLE jc_app;

INSERT INTO public.user_notifications(
  recipient_id, actor_id, type, target_type, target_id, dedupe_key
)
SELECT r.id, a.id, v.type, v.target_type, v.target_id, v.dedupe_key
FROM public.app_users r
CROSS JOIN public.app_users a
CROSS JOIN (VALUES
  ('crew_application', 'crew', 8101::bigint, 'pf8-smoke:crew-application'),
  ('crew_approved', 'crew', 8102::bigint, 'pf8-smoke:crew-approved'),
  ('crew_rejected', 'crew', 8103::bigint, 'pf8-smoke:crew-rejected'),
  ('post_comment', 'post', 8201::bigint, 'pf8-smoke:post-comment'),
  ('comment_reply', 'post', 8202::bigint, 'pf8-smoke:comment-reply')
) AS v(type, target_type, target_id, dedupe_key)
WHERE r.username = 'pf8_recipient'
  AND a.username = 'pf8_actor';

DO $$
DECLARE
  v_recipient_id bigint;
  v_actor_id bigint;
BEGIN
  SELECT id INTO v_recipient_id
  FROM public.app_users
  WHERE username = 'pf8_recipient';

  SELECT id INTO v_actor_id
  FROM public.app_users
  WHERE username = 'pf8_actor';

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'post_comment', 'crew', 8301, 'pf8-smoke:bad-post-comment-crew'
    );
    RAISE EXCEPTION 'post_comment/crew unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'comment_reply', 'crew', 8302, 'pf8-smoke:bad-comment-reply-crew'
    );
    RAISE EXCEPTION 'comment_reply/crew unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'crew_application', 'post', 8303, 'pf8-smoke:bad-crew-application-post'
    );
    RAISE EXCEPTION 'crew_application/post unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'unallocated_event', 'post', 8304, 'pf8-smoke:bad-unknown-type'
    );
    RAISE EXCEPTION 'Unallocated notification type unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END;
$$;

RESET ROLE;

DO $$
DECLARE
  v_valid_count integer;
BEGIN
  SELECT count(*)
    INTO v_valid_count
  FROM public.user_notifications
  WHERE dedupe_key LIKE 'pf8-smoke:%'
    AND dedupe_key NOT LIKE 'pf8-smoke:bad-%';

  IF v_valid_count <> 5 THEN
    RAISE EXCEPTION 'Expected five valid PF8 smoke notification pairs, got %.', v_valid_count;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.user_notifications
    WHERE dedupe_key LIKE 'pf8-smoke:bad-%'
  ) THEN
    RAISE EXCEPTION 'Invalid PF8 notification pair was persisted.';
  END IF;
END;
$$;

ROLLBACK;
