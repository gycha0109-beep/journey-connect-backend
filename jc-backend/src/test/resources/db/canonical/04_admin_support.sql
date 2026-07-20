-- Journey Connect DB v1.8 - Admin support and moderation migration
-- Target: PostgreSQL 15+
-- Prerequisite: 01_initial_schema.sql already applied.
--
-- Audit design note:
-- reports keep live foreign keys to their reported targets.
-- admin_actions deliberately stores immutable IDs and JSON snapshots without target FKs,
-- so source records can be deleted later without breaking audit retention.

BEGIN;

ALTER TABLE public.app_users
  ADD COLUMN role varchar(20) NOT NULL DEFAULT 'user',
  ADD COLUMN account_status varchar(20) NOT NULL DEFAULT 'active',
  ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('user', 'moderator', 'admin')),
  ADD CONSTRAINT app_users_account_status_check
    CHECK (account_status IN ('active', 'suspended', 'withdrawn'));

-- Moderation state is intentionally separate from the author's content lifecycle.
-- Authors may move posts between draft/published/deleted, but only privileged admin
-- functions may change moderation_status.
ALTER TABLE public.posts
  ADD COLUMN moderation_status varchar(20) NOT NULL DEFAULT 'visible',
  ADD COLUMN moderated_at timestamptz,
  ADD CONSTRAINT posts_moderation_status_check
    CHECK (moderation_status IN ('visible', 'hidden')),
  ADD CONSTRAINT posts_moderation_lifecycle_check CHECK (
    (moderation_status = 'visible' AND moderated_at IS NULL)
    OR (moderation_status = 'hidden' AND moderated_at IS NOT NULL)
  );

CREATE OR REPLACE FUNCTION public.set_post_moderation_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.moderation_status = 'hidden' THEN
    IF TG_OP = 'INSERT' OR OLD.moderation_status IS DISTINCT FROM 'hidden' THEN
      NEW.moderated_at := CURRENT_TIMESTAMP;
    ELSE
      NEW.moderated_at := OLD.moderated_at;
    END IF;
  ELSE
    NEW.moderated_at := NULL;
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER posts_set_moderation_lifecycle
BEFORE INSERT OR UPDATE OF moderation_status, moderated_at ON public.posts
FOR EACH ROW EXECUTE FUNCTION public.set_post_moderation_lifecycle();

-- User deletion and moderation deletion are separate so a normal application role
-- cannot undo an administrator's moderation action.
ALTER TABLE public.comments
  ADD COLUMN moderation_deleted_at timestamptz;

DROP INDEX IF EXISTS public.posts_author_published_idx;
DROP INDEX IF EXISTS public.posts_region_feed_idx;
DROP INDEX IF EXISTS public.posts_public_feed_idx;
DROP INDEX IF EXISTS public.comments_post_created_idx;

CREATE INDEX posts_author_published_idx
ON public.posts (author_id, published_at DESC, id DESC)
WHERE status = 'published' AND moderation_status = 'visible';

CREATE INDEX posts_region_feed_idx
ON public.posts (main_region_id, published_at DESC, id DESC)
WHERE status = 'published'
  AND visibility = 'public'
  AND moderation_status = 'visible';

CREATE INDEX posts_public_feed_idx
ON public.posts (published_at DESC, id DESC)
WHERE status = 'published'
  AND visibility = 'public'
  AND moderation_status = 'visible';

CREATE INDEX posts_moderation_queue_idx
ON public.posts (moderation_status, moderated_at DESC, id DESC)
WHERE moderation_status = 'hidden';

CREATE INDEX comments_post_created_idx
ON public.comments (post_id, created_at, id)
WHERE deleted_at IS NULL AND moderation_deleted_at IS NULL;

CREATE INDEX comments_moderation_queue_idx
ON public.comments (moderation_deleted_at DESC, id DESC)
WHERE moderation_deleted_at IS NOT NULL;

CREATE INDEX app_users_admin_filter_idx
ON public.app_users (role, account_status, created_at DESC)
WHERE role IN ('moderator', 'admin') OR account_status <> 'active';

CREATE OR REPLACE FUNCTION public.assert_staff_user(p_user_id bigint)
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
  v_role varchar(20);
  v_account_status varchar(20);
BEGIN
  IF p_user_id IS NULL THEN
    RETURN;
  END IF;

  SELECT u.role, u.account_status
    INTO v_role, v_account_status
  FROM public.app_users u
  WHERE u.id = p_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Staff user % does not exist.', p_user_id
      USING ERRCODE = '23503';
  END IF;

  IF v_role NOT IN ('moderator', 'admin') OR v_account_status <> 'active' THEN
    RAISE EXCEPTION 'User % is not an active moderator or admin.', p_user_id
      USING ERRCODE = '23514';
  END IF;
END;
$$;

CREATE TABLE public.reports (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reporter_id bigint REFERENCES public.app_users(id) ON DELETE SET NULL,
  target_type varchar(20) NOT NULL
    CHECK (target_type IN ('user', 'post', 'comment')),
  target_entity_id bigint NOT NULL CHECK (target_entity_id > 0),
  target_user_id bigint REFERENCES public.app_users(id) ON DELETE SET NULL,
  target_post_id bigint REFERENCES public.posts(id) ON DELETE SET NULL,
  target_comment_id bigint REFERENCES public.comments(id) ON DELETE SET NULL,
  target_snapshot jsonb NOT NULL,
  reason_category varchar(30) NOT NULL
    CHECK (reason_category IN (
      'spam',
      'harassment',
      'hate',
      'sexual_content',
      'violence',
      'misinformation',
      'privacy',
      'copyright',
      'other'
    )),
  reason_detail varchar(1000) NOT NULL DEFAULT '',
  status varchar(20) NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'in_review', 'resolved', 'rejected')),
  handled_by_user_id bigint,
  handled_by_username varchar(30),
  handled_by_role varchar(20)
    CHECK (handled_by_role IS NULL OR handled_by_role IN ('moderator', 'admin')),
  resolution_note varchar(1000),
  handled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT reports_at_most_one_live_target_fk_check CHECK (
    num_nonnulls(target_user_id, target_post_id, target_comment_id) <= 1
  ),
  CONSTRAINT reports_live_target_fk_match_check CHECK (
    (target_user_id IS NULL OR (target_type = 'user' AND target_user_id = target_entity_id))
    AND (target_post_id IS NULL OR (target_type = 'post' AND target_post_id = target_entity_id))
    AND (target_comment_id IS NULL OR (target_type = 'comment' AND target_comment_id = target_entity_id))
  ),
  CONSTRAINT reports_target_snapshot_object_check CHECK (
    jsonb_typeof(target_snapshot) = 'object'
  ),
  CONSTRAINT reports_target_snapshot_identity_check CHECK (
    target_snapshot ? 'type'
    AND target_snapshot ? 'id'
    AND target_snapshot ->> 'type' = target_type
    AND target_snapshot ->> 'id' = target_entity_id::text
  ),
  CONSTRAINT reports_reason_detail_length_check CHECK (
    char_length(reason_detail) <= 1000
  ),
  CONSTRAINT reports_resolution_lifecycle_check CHECK (
    (
      status = 'pending'
      AND handled_by_user_id IS NULL
      AND handled_by_username IS NULL
      AND handled_by_role IS NULL
      AND handled_at IS NULL
      AND resolution_note IS NULL
    )
    OR (
      status = 'in_review'
      AND handled_by_user_id IS NOT NULL
      AND handled_by_username IS NOT NULL
      AND handled_by_role IS NOT NULL
      AND handled_at IS NULL
      AND resolution_note IS NULL
    )
    OR (
      status IN ('resolved', 'rejected')
      AND handled_by_user_id IS NOT NULL
      AND handled_by_username IS NOT NULL
      AND handled_by_role IS NOT NULL
      AND handled_at IS NOT NULL
      AND char_length(btrim(COALESCE(resolution_note, ''))) BETWEEN 1 AND 1000
    )
  )
);

CREATE OR REPLACE FUNCTION public.validate_report_live_target_on_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF num_nonnulls(NEW.target_user_id, NEW.target_post_id, NEW.target_comment_id) <> 1 THEN
    RAISE EXCEPTION 'A new report must reference exactly one live target row.'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER reports_validate_live_target_on_insert
BEFORE INSERT ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.validate_report_live_target_on_insert();

CREATE OR REPLACE FUNCTION public.prevent_report_evidence_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.target_type IS DISTINCT FROM OLD.target_type
     OR NEW.target_entity_id IS DISTINCT FROM OLD.target_entity_id
     OR NEW.target_snapshot IS DISTINCT FROM OLD.target_snapshot
     OR NEW.reason_category IS DISTINCT FROM OLD.reason_category
     OR NEW.reason_detail IS DISTINCT FROM OLD.reason_detail
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'Report identity and evidence fields are immutable.'
      USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER reports_prevent_evidence_mutation
BEFORE UPDATE ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.prevent_report_evidence_mutation();

CREATE TRIGGER reports_set_updated_at
BEFORE UPDATE ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE FUNCTION public.validate_report_handler()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_role varchar(20);
  v_username varchar(30);
  v_account_status varchar(20);
BEGIN
  IF NEW.handled_by_user_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT u.role, u.username, u.account_status
    INTO v_role, v_username, v_account_status
  FROM public.app_users u
  WHERE u.id = NEW.handled_by_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Report handler % does not exist.', NEW.handled_by_user_id
      USING ERRCODE = '23503';
  END IF;

  IF v_role NOT IN ('moderator', 'admin') OR v_account_status <> 'active' THEN
    RAISE EXCEPTION 'Report handler % is not active staff.', NEW.handled_by_user_id
      USING ERRCODE = '23514';
  END IF;

  IF NEW.handled_by_role IS DISTINCT FROM v_role
     OR NEW.handled_by_username IS DISTINCT FROM v_username THEN
    RAISE EXCEPTION 'Report handler snapshot does not match current staff account.'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER reports_validate_handler
BEFORE INSERT OR UPDATE OF handled_by_user_id, handled_by_username, handled_by_role, status
ON public.reports
FOR EACH ROW EXECUTE FUNCTION public.validate_report_handler();

CREATE INDEX reports_status_created_idx
ON public.reports (status, created_at, id);
CREATE INDEX reports_target_lookup_idx
ON public.reports (target_type, target_entity_id, status, created_at DESC);
CREATE INDEX reports_target_user_idx
ON public.reports (target_user_id, status, created_at DESC)
WHERE target_user_id IS NOT NULL;
CREATE INDEX reports_target_post_idx
ON public.reports (target_post_id, status, created_at DESC)
WHERE target_post_id IS NOT NULL;
CREATE INDEX reports_target_comment_idx
ON public.reports (target_comment_id, status, created_at DESC)
WHERE target_comment_id IS NOT NULL;
CREATE INDEX reports_handler_idx
ON public.reports (handled_by_user_id, status, updated_at DESC)
WHERE handled_by_user_id IS NOT NULL;

CREATE UNIQUE INDEX reports_open_target_uq
ON public.reports (reporter_id, target_type, target_entity_id)
WHERE reporter_id IS NOT NULL
  AND status IN ('pending', 'in_review');

CREATE TABLE public.admin_actions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_user_id bigint NOT NULL,
  actor_username varchar(30) NOT NULL,
  actor_role varchar(20) NOT NULL
    CHECK (actor_role IN ('moderator', 'admin')),
  action_type varchar(40) NOT NULL
    CHECK (action_type IN (
      'user_suspend',
      'user_restore',
      'user_withdraw',
      'user_role_change',
      'post_hide',
      'post_restore',
      'comment_delete',
      'comment_restore',
      'report_start_review',
      'report_resolve',
      'report_reject',
      'other'
    )),
  target_type varchar(20) NOT NULL
    CHECK (target_type IN ('user', 'post', 'comment', 'report')),
  target_entity_id bigint NOT NULL,
  target_snapshot jsonb NOT NULL,
  reason varchar(1000) NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT admin_actions_reason_not_blank_check CHECK (
    char_length(btrim(reason)) BETWEEN 1 AND 1000
  ),
  CONSTRAINT admin_actions_target_snapshot_object_check CHECK (
    jsonb_typeof(target_snapshot) = 'object'
  ),
  CONSTRAINT admin_actions_target_snapshot_identity_check CHECK (
    target_snapshot ? 'type'
    AND target_snapshot ? 'id'
    AND target_snapshot ->> 'type' = target_type
    AND target_snapshot ->> 'id' = target_entity_id::text
  ),
  CONSTRAINT admin_actions_metadata_object_check CHECK (
    jsonb_typeof(metadata) = 'object'
  )
);

CREATE OR REPLACE FUNCTION public.validate_admin_action_actor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_role varchar(20);
  v_username varchar(30);
  v_account_status varchar(20);
BEGIN
  SELECT u.role, u.username, u.account_status
    INTO v_role, v_username, v_account_status
  FROM public.app_users u
  WHERE u.id = NEW.actor_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Audit actor % does not exist.', NEW.actor_user_id
      USING ERRCODE = '23503';
  END IF;

  IF v_role NOT IN ('moderator', 'admin') OR v_account_status <> 'active' THEN
    RAISE EXCEPTION 'Audit actor % is not active staff.', NEW.actor_user_id
      USING ERRCODE = '23514';
  END IF;

  IF NEW.actor_role IS DISTINCT FROM v_role
     OR NEW.actor_username IS DISTINCT FROM v_username THEN
    RAISE EXCEPTION 'Audit actor snapshot does not match current staff account.'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER admin_actions_validate_actor
BEFORE INSERT ON public.admin_actions
FOR EACH ROW EXECUTE FUNCTION public.validate_admin_action_actor();

CREATE INDEX admin_actions_actor_created_idx
ON public.admin_actions (actor_user_id, created_at DESC, id DESC);
CREATE INDEX admin_actions_target_created_idx
ON public.admin_actions (target_type, target_entity_id, created_at DESC, id DESC);
CREATE INDEX admin_actions_action_created_idx
ON public.admin_actions (action_type, created_at DESC, id DESC);


-- Once reporting exists, permanent post deletion must not erase an open post/comment report.
-- Resolved/rejected reports may be removed with their source content after the one-year hold.
CREATE OR REPLACE FUNCTION public.purge_expired_deleted_posts()
RETURNS bigint
LANGUAGE plpgsql
AS $$
DECLARE
  v_deleted_count bigint;
BEGIN
  WITH deleted_rows AS (
    DELETE FROM public.posts p
    WHERE p.status = 'deleted'
      AND p.purge_after <= CURRENT_TIMESTAMP
      AND NOT EXISTS (
        SELECT 1
        FROM public.reports r
        WHERE r.status IN ('pending', 'in_review')
          AND (
            (r.target_type = 'post' AND r.target_entity_id = p.id)
            OR (
              r.target_type = 'comment'
              AND EXISTS (
                SELECT 1
                FROM public.comments c
                WHERE c.post_id = p.id
                  AND c.id = r.target_entity_id
              )
            )
          )
      )
    RETURNING p.id
  )
  SELECT count(*) INTO v_deleted_count FROM deleted_rows;

  RETURN v_deleted_count;
END;
$$;

COMMIT;
