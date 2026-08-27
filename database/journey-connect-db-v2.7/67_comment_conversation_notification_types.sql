-- Journey Connect DB v2.7 - PF8 Comment conversation notification types
-- Target: PostgreSQL 15+
-- Prerequisite: 66_comment_replies_smoke_test.sql

BEGIN;

ALTER TABLE public.user_notifications
  DROP CONSTRAINT user_notifications_type_check;

ALTER TABLE public.user_notifications
  DROP CONSTRAINT user_notifications_target_type_check;

ALTER TABLE public.user_notifications
  ADD CONSTRAINT user_notifications_type_check CHECK (
    type IN (
      'crew_application',
      'crew_approved',
      'crew_rejected',
      'post_comment',
      'comment_reply'
    )
  );

ALTER TABLE public.user_notifications
  ADD CONSTRAINT user_notifications_target_type_check CHECK (
    (type IN ('crew_application', 'crew_approved', 'crew_rejected') AND target_type = 'crew')
    OR
    (type IN ('post_comment', 'comment_reply') AND target_type = 'post')
  );

COMMIT;
