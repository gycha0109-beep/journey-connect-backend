-- Journey Connect DB v2.7 - PF10 post like notification type
-- Target: PostgreSQL 15+
-- Prerequisite: 68_comment_conversation_notification_types_smoke_test.sql

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
      'comment_reply',
      'post_like'
    )
  );

ALTER TABLE public.user_notifications
  ADD CONSTRAINT user_notifications_target_type_check CHECK (
    (type IN ('crew_application', 'crew_approved', 'crew_rejected') AND target_type = 'crew')
    OR
    (type IN ('post_comment', 'comment_reply', 'post_like') AND target_type = 'post')
  );

COMMIT;
