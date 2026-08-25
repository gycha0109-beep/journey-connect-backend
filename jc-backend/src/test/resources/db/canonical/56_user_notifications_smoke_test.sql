-- Journey Connect DB v2.7 - PF2 user notification runtime smoke test
-- Target: PostgreSQL 15+

BEGIN;

DO $$
DECLARE
  v_public_acl_count integer;
BEGIN
  IF to_regclass('public.user_notifications') IS NULL THEN
    RAISE EXCEPTION 'user_notifications table is missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conrelid = 'public.user_notifications'::regclass
      AND c.conname = 'user_notifications_dedupe_key_uq'
      AND c.contype = 'u'
  ) THEN
    RAISE EXCEPTION 'user_notifications dedupe unique constraint is missing.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'user_notifications'
      AND indexname = 'user_notifications_recipient_unread_idx'
  ) THEN
    RAISE EXCEPTION 'user_notifications unread index is missing.';
  END IF;

  IF NOT has_table_privilege('jc_app', 'public.user_notifications', 'SELECT')
     OR NOT has_table_privilege('jc_app', 'public.user_notifications', 'INSERT') THEN
    RAISE EXCEPTION 'jc_app must read and insert user notifications.';
  END IF;

  IF NOT has_column_privilege('jc_app', 'public.user_notifications', 'read_at', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must update user_notifications.read_at.';
  END IF;

  IF has_column_privilege('jc_app', 'public.user_notifications', 'type', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.user_notifications', 'recipient_id', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'DELETE')
     OR has_table_privilege('jc_app', 'public.user_notifications', 'TRUNCATE') THEN
    RAISE EXCEPTION 'jc_app notification mutation privileges are too broad.';
  END IF;

  IF has_table_privilege('jc_auth', 'public.user_notifications', 'SELECT')
     OR has_table_privilege('jc_auth', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_admin', 'public.user_notifications', 'INSERT')
     OR has_table_privilege('jc_recommendation', 'public.user_notifications', 'SELECT') THEN
    RAISE EXCEPTION 'Non-app runtime roles must not own notification runtime access.';
  END IF;

  SELECT count(*)
    INTO v_public_acl_count
  FROM pg_class c
  CROSS JOIN LATERAL aclexplode(COALESCE(c.relacl, acldefault('r', c.relowner))) acl
  WHERE c.oid = 'public.user_notifications'::regclass
    AND acl.grantee = 0;

  IF v_public_acl_count <> 0 THEN
    RAISE EXCEPTION 'PUBLIC must not have user_notifications privileges.';
  END IF;

  IF NOT has_sequence_privilege('jc_app', 'public.user_notifications_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'jc_app must use user_notifications identity sequence.';
  END IF;
END;
$$;

ROLLBACK;
