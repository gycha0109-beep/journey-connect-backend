-- Journey Connect DB v2.7 - PF10 post like notification type smoke test
-- Target: PostgreSQL 15+
-- Prerequisite: 69_post_like_notification_type.sql
-- Run as a PostgreSQL superuser because this script uses SET ROLE.

BEGIN;

DO $$
BEGIN
  IF NOT has_table_privilege('jc_app', 'public.user_notifications', 'SELECT')
     OR NOT has_table_privilege('jc_app', 'public.user_notifications', 'INSERT')
     OR NOT has_column_privilege('jc_app', 'public.user_notifications', 'read_at', 'UPDATE') THEN
    RAISE EXCEPTION 'PF10 must preserve jc_app notification runtime authority.';
  END IF;

  IF has_column_privilege('jc_app', 'public.user_notifications', 'type', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.user_notifications', 'recipient_id', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'DELETE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'TRUNCATE') THEN
    RAISE EXCEPTION 'PF10 widened jc_app notification mutation privileges.';
  END IF;

  IF has_table_privilege('jc_auth', 'public.user_notifications', 'SELECT')
     OR has_table_privilege('jc_auth', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_admin', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_recommendation', 'public.user_notifications', 'SELECT')
     OR has_table_privilege('jc_recommendation', 'public.user_notifications', 'INSERT') THEN
    RAISE EXCEPTION 'PF10 leaked notification runtime authority outside jc_app.';
  END IF;

  IF NOT has_sequence_privilege('jc_app', 'public.user_notifications_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'PF10 must preserve jc_app notification identity sequence usage.';
  END IF;
END;
$$;

INSERT INTO public.app_users (email, password_hash, username, display_name)
VALUES
  ('pf10-recipient@example.test', 'hash', 'pf10_recipient', 'PF10 Recipient'),
  ('pf10-actor@example.test', 'hash', 'pf10_actor', 'PF10 Actor');

SET LOCAL ROLE jc_app;

INSERT INTO public.user_notifications(
  recipient_id, actor_id, type, target_type, target_id, dedupe_key
)
SELECT r.id, a.id, v.type, v.target_type, v.target_id, v.dedupe_key
FROM public.app_users r
CROSS JOIN public.app_users a
CROSS JOIN (VALUES
  ('crew_application', 'crew', 10101::bigint, 'pf10-smoke:crew-application'),
  ('crew_approved', 'crew', 10102::bigint, 'pf10-smoke:crew-approved'),
  ('crew_rejected', 'crew', 10103::bigint, 'pf10-smoke:crew-rejected'),
  ('post_comment', 'post', 10201::bigint, 'pf10-smoke:post-comment'),
  ('comment_reply', 'post', 10202::bigint, 'pf10-smoke:comment-reply'),
  ('post_like', 'post', 10203::bigint, 'pf10-smoke:post-like')
) AS v(type, target_type, target_id, dedupe_key)
WHERE r.username = 'pf10_recipient'
  AND a.username = 'pf10_actor';

DO $$
DECLARE
  v_recipient_id bigint;
  v_actor_id bigint;
BEGIN
  SELECT id INTO v_recipient_id FROM public.app_users WHERE username = 'pf10_recipient';
  SELECT id INTO v_actor_id FROM public.app_users WHERE username = 'pf10_actor';

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'post_like', 'crew', 10301, 'pf10-smoke:bad-post-like-crew'
    );
    RAISE EXCEPTION 'post_like/crew unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'crew_application', 'post', 10302, 'pf10-smoke:bad-crew-post'
    );
    RAISE EXCEPTION 'crew_application/post unexpectedly succeeded.';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.user_notifications(
      recipient_id, actor_id, type, target_type, target_id, dedupe_key
    ) VALUES (
      v_recipient_id, v_actor_id, 'unallocated_event', 'post', 10303, 'pf10-smoke:bad-unknown-type'
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
  SELECT count(*) INTO v_valid_count
  FROM public.user_notifications
  WHERE dedupe_key LIKE 'pf10-smoke:%'
    AND dedupe_key NOT LIKE 'pf10-smoke:bad-%';

  IF v_valid_count <> 6 THEN
    RAISE EXCEPTION 'Expected six valid PF10 notification pairs, got %.', v_valid_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.user_notifications
    WHERE dedupe_key LIKE 'pf10-smoke:bad-%'
  ) THEN
    RAISE EXCEPTION 'Invalid PF10 notification pair was persisted.';
  END IF;
END;
$$;

ROLLBACK;
