-- Journey Connect DB v2.7 - PF2 user notification runtime
-- Target: PostgreSQL 15+
-- Prerequisite: 05_security_roles.sql and canonical app_users schema

BEGIN;

CREATE TABLE public.user_notifications (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  recipient_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  actor_id bigint REFERENCES public.app_users(id) ON DELETE SET NULL,
  type varchar(40) NOT NULL,
  target_type varchar(20) NOT NULL,
  target_id bigint NOT NULL,
  dedupe_key varchar(255) NOT NULL,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_notifications_type_check CHECK (
    type IN ('crew_application', 'crew_approved', 'crew_rejected')
  ),
  CONSTRAINT user_notifications_target_type_check CHECK (target_type = 'crew'),
  CONSTRAINT user_notifications_target_id_check CHECK (target_id > 0),
  CONSTRAINT user_notifications_dedupe_key_check CHECK (char_length(btrim(dedupe_key)) > 0),
  CONSTRAINT user_notifications_dedupe_key_uq UNIQUE (dedupe_key)
);

CREATE INDEX user_notifications_recipient_created_idx
ON public.user_notifications (recipient_id, created_at DESC, id DESC);

CREATE INDEX user_notifications_recipient_unread_idx
ON public.user_notifications (recipient_id, created_at DESC, id DESC)
WHERE read_at IS NULL;

REVOKE ALL ON public.user_notifications
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;
REVOKE ALL ON SEQUENCE public.user_notifications_id_seq
FROM PUBLIC, jc_app, jc_auth, jc_admin, jc_security_owner, jc_recommendation;

GRANT SELECT, INSERT ON public.user_notifications TO jc_app;
GRANT UPDATE (read_at) ON public.user_notifications TO jc_app;
GRANT USAGE ON SEQUENCE public.user_notifications_id_seq TO jc_app;

REVOKE DELETE, TRUNCATE ON public.user_notifications FROM jc_app;

COMMIT;
