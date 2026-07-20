-- Journey Connect DB v1.8 - Role-based security smoke test
-- Run after 05_security_roles.sql in a development database.
-- Run as a PostgreSQL superuser because this script uses SET ROLE for real-role tests.
-- All row changes are rolled back.

BEGIN;

DO $$
DECLARE
  v_function_owner text;
  v_function_config text;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_app')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_admin')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_auth')
     OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jc_security_owner') THEN
    RAISE EXCEPTION 'Expected security roles do not exist.';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper
  ) THEN
    RAISE EXCEPTION '06_security_smoke_test.sql must run as a superuser for SET ROLE tests.';
  END IF;

  IF pg_has_role('jc_admin', 'jc_app', 'MEMBER') THEN
    RAISE EXCEPTION 'jc_admin must not be a member of jc_app.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname IN ('jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner')
      AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolcanlogin OR rolreplication OR rolbypassrls)
  ) THEN
    RAISE EXCEPTION 'Journey Connect group roles must remain NOLOGIN and non-privileged.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_auth_members m
    JOIN pg_roles member_role ON member_role.oid = m.member
    WHERE member_role.rolname IN ('jc_app', 'jc_auth', 'jc_admin', 'jc_security_owner')
  ) THEN
    RAISE EXCEPTION 'Journey Connect security roles must not inherit unrelated roles.';
  END IF;

  IF has_schema_privilege('jc_security_owner', 'public', 'CREATE') THEN
    RAISE EXCEPTION 'jc_security_owner must not retain CREATE on public after migration.';
  END IF;

  SELECT r.rolname, array_to_string(p.proconfig, ',')
    INTO v_function_owner, v_function_config
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  JOIN pg_roles r ON r.oid = p.proowner
  WHERE n.nspname = 'public'
    AND p.oid = 'public.admin_suspend_user(bigint,character varying)'::regprocedure;

  IF v_function_owner <> 'jc_security_owner' THEN
    RAISE EXCEPTION 'Privileged function is not owned by jc_security_owner.';
  END IF;
  IF v_function_config NOT LIKE '%search_path=pg_catalog, public, pg_temp%' THEN
    RAISE EXCEPTION 'Privileged function search_path is not hardened with pg_temp last.';
  END IF;

  IF has_column_privilege('jc_app', 'public.app_users', 'password_hash', 'SELECT')
     OR has_column_privilege('jc_admin', 'public.app_users', 'password_hash', 'SELECT') THEN
    RAISE EXCEPTION 'jc_app/jc_admin must not read password_hash.';
  END IF;
  IF NOT has_column_privilege('jc_auth', 'public.app_users', 'password_hash', 'SELECT') THEN
    RAISE EXCEPTION 'jc_auth must read password_hash.';
  END IF;

  IF has_column_privilege('jc_app', 'public.posts', 'moderation_status', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.posts', 'moderated_at', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must not update post moderation columns.';
  END IF;
  IF has_column_privilege('jc_app', 'public.comments', 'moderation_deleted_at', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must not update comment moderation state.';
  END IF;
  IF has_table_privilege('jc_app', 'public.comments', 'DELETE') THEN
    RAISE EXCEPTION 'jc_app must not physically delete comments.';
  END IF;

  IF has_column_privilege('jc_app', 'public.posts', 'view_count', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must not directly update view_count.';
  END IF;
  IF has_table_privilege('jc_app', 'public.places', 'UPDATE') THEN
    RAISE EXCEPTION 'jc_app must not directly update shared place rows.';
  END IF;

  IF has_column_privilege('jc_app', 'public.post_images', 'post_id', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.post_images', 'created_at', 'UPDATE')
     OR NOT has_column_privilege('jc_app', 'public.post_images', 'caption', 'UPDATE') THEN
    RAISE EXCEPTION 'post_images column-level UPDATE privileges are not least-privilege.';
  END IF;
  IF has_column_privilege('jc_app', 'public.post_places', 'post_id', 'UPDATE')
     OR has_column_privilege('jc_app', 'public.post_places', 'created_at', 'UPDATE')
     OR NOT has_column_privilege('jc_app', 'public.post_places', 'memo', 'UPDATE') THEN
    RAISE EXCEPTION 'post_places column-level UPDATE privileges are not least-privilege.';
  END IF;
  IF has_table_privilege('jc_app', 'public.post_tags', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.post_likes', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.bookmarks', 'UPDATE')
     OR has_table_privilege('jc_app', 'public.follows', 'UPDATE') THEN
    RAISE EXCEPTION 'Set-like link tables must not grant UPDATE to jc_app.';
  END IF;
  IF NOT has_function_privilege(
      'jc_app', 'public.increment_post_view(bigint)', 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'jc_app lacks the controlled view-count increment function.';
  END IF;

  IF NOT has_function_privilege(
      'jc_app', 'public.assert_published_post_integrity(bigint)', 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'jc_app lacks the deferred published-post integrity helper permission.';
  END IF;
  IF has_function_privilege(
       'jc_app', 'public.set_post_updated_at_except_view_count()', 'EXECUTE'
     ) OR has_function_privilege(
       'jc_app', 'public.prevent_hidden_post_child_mutation()', 'EXECUTE'
     ) THEN
    RAISE EXCEPTION 'Trigger-only functions must not be callable as jc_app entry points.';
  END IF;

  IF has_sequence_privilege('jc_app', 'public.reports_id_seq', 'USAGE')
     OR has_sequence_privilege('jc_app', 'public.admin_actions_id_seq', 'USAGE')
     OR has_sequence_privilege('jc_auth', 'public.reports_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'Runtime roles have an unrelated admin sequence permission.';
  END IF;
  IF NOT has_sequence_privilege('jc_app', 'public.posts_id_seq', 'USAGE')
     OR NOT has_sequence_privilege('jc_auth', 'public.app_users_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'Runtime roles lack a required identity-sequence permission.';
  END IF;

  IF has_table_privilege('jc_admin', 'public.posts', 'UPDATE')
     OR has_table_privilege('jc_admin', 'public.comments', 'UPDATE')
     OR has_table_privilege('jc_admin', 'public.reports', 'UPDATE')
     OR has_table_privilege('jc_admin', 'public.admin_actions', 'INSERT') THEN
    RAISE EXCEPTION 'jc_admin has a forbidden direct mutation privilege.';
  END IF;
END;
$$;

-- Test identities are created by the migration/superuser account. Runtime roles cannot
-- assign role or account_status directly.
INSERT INTO public.app_users (email, password_hash, username, display_name, role)
VALUES
  ('v18-admin@example.test', 'hash', 'v18_security_admin', 'Security Admin', 'admin'),
  ('v18-mod@example.test', 'hash', 'v18_security_mod', 'Security Moderator', 'moderator'),
  ('v18-user@example.test', 'hash', 'v18_security_user', 'Security User', 'user'),
  ('v18-target@example.test', 'hash', 'v18_security_target', 'Security Target', 'user');

-- Missing request identity must fail even when the DB role itself is valid.
SET LOCAL ROLE jc_app;
SELECT set_config('jc.current_user_id', '', true);
DO $$
BEGIN
  BEGIN
    PERFORM public.submit_report(
      'user',
      (SELECT id FROM public.app_users WHERE username = 'v18_security_target'),
      'other',
      'must fail without request identity'
    );
    RAISE EXCEPTION 'Report submission without request identity succeeded.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

-- Execute the real normal-application write path as jc_app. Publishing forces deferred
-- integrity checks to execute after PUBLIC function privileges have been revoked.
SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);

INSERT INTO public.places (
  region_id, name_local, name_ko, category, created_by_user_id
)
SELECT r.id, 'v1.8 Security Place', 'v1.8 보안 테스트 장소', 'cafe', u.id
FROM public.regions r
CROSS JOIN public.app_users u
WHERE r.slug = 'kr-seoul-seongsu'
  AND u.username = 'v18_security_user';

INSERT INTO public.posts (author_id, main_region_id, title, content, status)
SELECT u.id, r.id, 'v1.8 role path post', 'real jc_app publishing path', 'draft'
FROM public.app_users u
CROSS JOIN public.regions r
WHERE u.username = 'v18_security_user'
  AND r.slug = 'kr-seoul';

INSERT INTO public.post_places (post_id, place_id, sort_order)
SELECT p.id, pl.id, 0
FROM public.posts p
JOIN public.app_users u ON u.id = p.author_id
CROSS JOIN public.places pl
WHERE u.username = 'v18_security_user'
  AND p.title = 'v1.8 role path post'
  AND pl.name_local = 'v1.8 Security Place';

UPDATE public.posts
SET status = 'published'
WHERE title = 'v1.8 role path post'
  AND author_id = (SELECT id FROM public.app_users WHERE username = 'v18_security_user');

SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO public.post_images (post_id, image_url, alt_text, caption, sort_order)
SELECT p.id, 'https://example.test/v1.8-evidence.jpg', 'evidence image', 'original caption', 0
FROM public.posts p
WHERE p.title = 'v1.8 role path post';

INSERT INTO public.posts (author_id, main_region_id, title, content, visibility, status)
SELECT u.id, r.id, 'v1.8 private post', 'private evidence', 'private', 'draft'
FROM public.app_users u
CROSS JOIN public.regions r
WHERE u.username = 'v18_security_user'
  AND r.slug = 'kr-seoul';
INSERT INTO public.post_places (post_id, place_id, sort_order)
SELECT p.id, pl.id, 0
FROM public.posts p
CROSS JOIN public.places pl
WHERE p.title = 'v1.8 private post'
  AND pl.name_local = 'v1.8 Security Place';
UPDATE public.posts
SET status = 'published'
WHERE title = 'v1.8 private post';
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

DO $$
DECLARE
  v_before_updated_at timestamptz;
  v_after_updated_at timestamptz;
  v_new_count bigint;
BEGIN
  SELECT updated_at INTO v_before_updated_at
  FROM public.posts
  WHERE title = 'v1.8 role path post';

  SELECT public.increment_post_view(
    (SELECT id FROM public.posts WHERE title = 'v1.8 role path post')
  ) INTO v_new_count;

  SELECT updated_at INTO v_after_updated_at
  FROM public.posts
  WHERE title = 'v1.8 role path post';

  IF v_new_count <> 1 THEN
    RAISE EXCEPTION 'Controlled view increment returned %, expected 1.', v_new_count;
  END IF;
  IF v_after_updated_at IS DISTINCT FROM v_before_updated_at THEN
    RAISE EXCEPTION 'Pure view increment unexpectedly changed posts.updated_at.';
  END IF;
END;
$$;

DO $$
DECLARE
  v_private_count bigint;
BEGIN
  SELECT public.increment_post_view(
    (SELECT id FROM public.posts WHERE title = 'v1.8 private post')
  ) INTO v_private_count;
  IF v_private_count <> 1 THEN
    RAISE EXCEPTION 'Private post owner view increment failed.';
  END IF;
END;
$$;

INSERT INTO public.comments (post_id, author_id, content)
SELECT p.id, u.id, 'v1.8 moderation test comment'
FROM public.posts p
JOIN public.app_users u ON u.username = 'v18_security_user'
WHERE p.title = 'v1.8 role path post'
  AND p.author_id = u.id;

DO $$
BEGIN
  BEGIN
    UPDATE public.posts
    SET moderation_status = 'hidden'
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_app directly changed post moderation_status.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.comments
    SET moderation_deleted_at = CURRENT_TIMESTAMP
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'jc_app directly changed comment moderation state.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    DELETE FROM public.comments
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'jc_app physically deleted a comment.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.posts
    SET view_count = 999999
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_app directly changed view_count.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.places
    SET name_local = 'forbidden shared place mutation'
    WHERE name_local = 'v1.8 Security Place';
    RAISE EXCEPTION 'jc_app directly changed a shared place row.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.post_images
    SET post_id = post_id
    WHERE image_url = 'https://example.test/v1.8-evidence.jpg';
    RAISE EXCEPTION 'jc_app changed immutable post_images.post_id.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.post_likes
    SET created_at = created_at;
    RAISE EXCEPTION 'jc_app updated a set-like post_likes row.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    PERFORM nextval('public.reports_id_seq');
    RAISE EXCEPTION 'jc_app advanced the reports identity sequence.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

-- A different active user can report only content they can currently access. The report
-- captures immutable evidence before the author changes the live row.
SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_target'),
  true
);
SELECT public.submit_report(
  'post',
  (SELECT id FROM public.posts WHERE title = 'v1.8 role path post'),
  'other',
  'snapshot preservation test'
);
SELECT public.submit_report(
  'comment',
  (SELECT id FROM public.comments WHERE content = 'v1.8 moderation test comment'),
  'other',
  'comment snapshot preservation test'
);
DO $$
BEGIN
  BEGIN
    PERFORM public.submit_report(
      'post',
      (SELECT id FROM public.posts WHERE title = 'v1.8 private post'),
      'other',
      'must fail because reporter cannot view private post'
    );
    RAISE EXCEPTION 'Inaccessible private post was reportable.';
  EXCEPTION WHEN no_data_found THEN
    NULL;
  END;

  BEGIN
    PERFORM public.increment_post_view(
      (SELECT id FROM public.posts WHERE title = 'v1.8 private post')
    );
    RAISE EXCEPTION 'Non-owner incremented a private post view.';
  EXCEPTION WHEN no_data_found THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
UPDATE public.posts
SET content = 'edited after report but before moderation'
WHERE title = 'v1.8 role path post';
RESET ROLE;

-- The report table is intentionally unreadable to jc_app. Verify the immutable evidence
-- as the superuser test harness only after leaving the runtime role.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM public.reports r
    WHERE r.target_type = 'post'
      AND r.target_entity_id = (SELECT id FROM public.posts WHERE title = 'v1.8 role path post')
      AND r.target_snapshot ->> 'content' = 'real jc_app publishing path'
      AND jsonb_array_length(r.target_snapshot -> 'images') = 1
  ) THEN
    RAISE EXCEPTION 'Post report did not preserve the original evidence snapshot.';
  END IF;
END;
$$;

-- Execute moderation functions as the real jc_admin role. The application-user actor is
-- resolved from the transaction-local request context.
SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
DO $$
BEGIN
  BEGIN
    UPDATE public.posts
    SET title = 'forbidden admin direct update'
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_admin directly updated a post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    PERFORM public.admin_change_user_role(
      (SELECT id FROM public.app_users WHERE username = 'v18_security_target'),
      'moderator',
      'moderator must not change roles'
    );
    RAISE EXCEPTION 'Moderator changed a user role.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
SELECT public.admin_hide_post(
  (SELECT id FROM public.posts WHERE title = 'v1.8 role path post'),
  'security smoke hide'
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.posts
    WHERE title = 'v1.8 role path post'
      AND status = 'published'
      AND moderation_status = 'hidden'
      AND moderated_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Admin hide did not preserve content status and set moderation state.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.admin_actions a
    WHERE a.action_type = 'post_hide'
      AND a.actor_username = 'v18_security_mod'
      AND a.actor_role = 'moderator'
      AND a.target_entity_id = (
        SELECT id FROM public.posts WHERE title = 'v1.8 role path post'
      )
  ) THEN
    RAISE EXCEPTION 'Admin audit actor was not derived from request context.';
  END IF;
END;
$$;
RESET ROLE;

-- A moderation-hidden post is evidence-locked. The ordinary app role can neither edit
-- the live content nor reverse the moderation state.
SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
DO $$
BEGIN
  BEGIN
    UPDATE public.posts
    SET content = 'forbidden edit while moderation-hidden'
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_app edited a moderation-hidden post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.posts
    SET moderation_status = 'visible'
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_app reversed an admin post hide.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.post_images
    SET caption = 'forbidden hidden-post image edit'
    WHERE image_url = 'https://example.test/v1.8-evidence.jpg';
    RAISE EXCEPTION 'jc_app edited an image belonging to a moderation-hidden post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    DELETE FROM public.post_images
    WHERE image_url = 'https://example.test/v1.8-evidence.jpg';
    RAISE EXCEPTION 'jc_app deleted an image belonging to a moderation-hidden post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.post_places
    SET memo = 'forbidden hidden-post place edit'
    WHERE post_id = (SELECT id FROM public.posts WHERE title = 'v1.8 role path post');
    RAISE EXCEPTION 'jc_app edited a place link belonging to a moderation-hidden post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    INSERT INTO public.post_tags (post_id, tag_id)
    SELECT p.id, t.id
    FROM public.posts p
    CROSS JOIN LATERAL (
      SELECT id FROM public.tags ORDER BY id LIMIT 1
    ) t
    WHERE p.title = 'v1.8 role path post';
    RAISE EXCEPTION 'jc_app inserted a tag on a moderation-hidden post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.post_images
    WHERE image_url = 'https://example.test/v1.8-evidence.jpg'
      AND caption = 'original caption'
  ) THEN
    RAISE EXCEPTION 'Hidden-post child evidence changed despite the mutation lock.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.post_tags pt
    JOIN public.posts p ON p.id = pt.post_id
    WHERE p.title = 'v1.8 role path post'
  ) THEN
    RAISE EXCEPTION 'Hidden-post tag insertion was not rolled back.';
  END IF;
END;
$$;

SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_admin'),
  true
);
SELECT public.admin_change_user_role(
  (SELECT id FROM public.app_users WHERE username = 'v18_security_target'),
  'moderator',
  'admin role-change smoke test'
);
SELECT public.admin_change_user_role(
  (SELECT id FROM public.app_users WHERE username = 'v18_security_target'),
  'user',
  'admin role-change rollback smoke test'
);
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
SELECT public.admin_restore_post(
  (SELECT id FROM public.posts WHERE title = 'v1.8 role path post'),
  'security smoke restore'
);
SELECT public.admin_delete_comment(
  (SELECT id FROM public.comments WHERE content = 'v1.8 moderation test comment'),
  'security smoke comment moderation'
);
RESET ROLE;

SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
DO $$
BEGIN
  BEGIN
    UPDATE public.comments
    SET content = 'forbidden edit while moderation-deleted'
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'jc_app edited a moderation-deleted comment.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.comments
    SET deleted_at = CURRENT_TIMESTAMP
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'jc_app changed user deletion state on a moderation-deleted comment.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.comments
    SET moderation_deleted_at = NULL
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'jc_app reversed an admin comment deletion.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
RESET ROLE;

SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
SELECT public.admin_restore_comment(
  (SELECT id FROM public.comments WHERE content = 'v1.8 moderation test comment'),
  'security smoke comment restore'
);
RESET ROLE;

-- Regression: an author must not evade moderation by user-deleting content before the
-- moderator acts and restoring it afterward.
SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
UPDATE public.posts
SET status = 'deleted'
WHERE title = 'v1.8 role path post';
UPDATE public.comments
SET deleted_at = CURRENT_TIMESTAMP
WHERE content = 'v1.8 moderation test comment';
RESET ROLE;

SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
SELECT public.admin_hide_post(
  (SELECT id FROM public.posts WHERE title = 'v1.8 role path post'),
  'moderate author-deleted post'
);
SELECT public.admin_delete_comment(
  (SELECT id FROM public.comments WHERE content = 'v1.8 moderation test comment'),
  'moderate author-deleted comment'
);
RESET ROLE;

SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
DO $$
BEGIN
  BEGIN
    UPDATE public.posts
    SET status = 'published'
    WHERE title = 'v1.8 role path post';
    RAISE EXCEPTION 'Author restored a moderation-hidden deleted post.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    UPDATE public.comments
    SET deleted_at = NULL
    WHERE content = 'v1.8 moderation test comment';
    RAISE EXCEPTION 'Author restored a moderation-deleted comment.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  IF NOT EXISTS (
    SELECT 1 FROM public.posts
    WHERE title = 'v1.8 role path post'
      AND status = 'deleted'
      AND moderation_status = 'hidden'
  ) THEN
    RAISE EXCEPTION 'Post moderation lock state was not preserved.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.comments
    WHERE content = 'v1.8 moderation test comment'
      AND deleted_at IS NOT NULL
      AND moderation_deleted_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'Comment moderation lock state was not preserved.';
  END IF;
END;
$$;
RESET ROLE;

-- Return the first test content to a neutral moderation state before purge tests.
SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
SELECT public.admin_restore_post(
  (SELECT id FROM public.posts WHERE title = 'v1.8 role path post'),
  'cleanup moderation bypass regression'
);
SELECT public.admin_restore_comment(
  (SELECT id FROM public.comments WHERE content = 'v1.8 moderation test comment'),
  'cleanup moderation bypass regression'
);
RESET ROLE;

-- Create an expirable deleted post with an open report. The purge function must retain
-- it until the report is closed, then delete it. System timestamps are backdated only by
-- the superuser test harness while the lifecycle trigger is temporarily disabled.
SET LOCAL ROLE jc_app;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
INSERT INTO public.posts (author_id, main_region_id, title, content, status)
SELECT u.id, r.id, 'v1.8 purge reported post', 'purge protection test', 'draft'
FROM public.app_users u
CROSS JOIN public.regions r
WHERE u.username = 'v18_security_user'
  AND r.slug = 'kr-seoul';
INSERT INTO public.post_places (post_id, place_id, sort_order)
SELECT p.id, pl.id, 0
FROM public.posts p
JOIN public.app_users u ON u.id = p.author_id
CROSS JOIN public.places pl
WHERE u.username = 'v18_security_user'
  AND p.title = 'v1.8 purge reported post'
  AND pl.name_local = 'v1.8 Security Place';
UPDATE public.posts
SET status = 'published'
WHERE title = 'v1.8 purge reported post';
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_target'),
  true
);
SELECT public.submit_report(
  'post',
  (SELECT id FROM public.posts WHERE title = 'v1.8 purge reported post'),
  'other',
  'open report protects one-year purge'
);
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_user'),
  true
);
UPDATE public.posts
SET status = 'deleted'
WHERE title = 'v1.8 purge reported post';
RESET ROLE;

-- Flush deferred post-integrity events before ALTER TABLE. PostgreSQL forbids ALTER
-- TABLE while a relation still has pending trigger events.
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

ALTER TABLE public.posts DISABLE TRIGGER posts_set_lifecycle;
UPDATE public.posts
SET deleted_at = CURRENT_TIMESTAMP - interval '2 years',
    purge_after = CURRENT_TIMESTAMP - interval '1 year'
WHERE title = 'v1.8 purge reported post';
ALTER TABLE public.posts ENABLE TRIGGER posts_set_lifecycle;

SELECT set_config(
  'jc.test_purge_post_id',
  (SELECT id::text FROM public.posts WHERE title = 'v1.8 purge reported post'),
  true
);
SELECT public.purge_expired_deleted_posts();
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.posts WHERE title = 'v1.8 purge reported post') THEN
    RAISE EXCEPTION 'Purge removed a post with an open report.';
  END IF;
END;
$$;

SET LOCAL ROLE jc_admin;
SELECT set_config(
  'jc.current_user_id',
  (SELECT id::text FROM public.app_users WHERE username = 'v18_security_mod'),
  true
);
SELECT public.admin_start_report_review(
  (SELECT id FROM public.reports
   WHERE target_type = 'post'
     AND target_entity_id = (SELECT id FROM public.posts WHERE title = 'v1.8 purge reported post'))
);
SELECT public.admin_finish_report(
  (SELECT id FROM public.reports
   WHERE target_type = 'post'
     AND target_entity_id = (SELECT id FROM public.posts WHERE title = 'v1.8 purge reported post')),
  'resolved',
  'report closed for purge test'
);
RESET ROLE;

SELECT public.purge_expired_deleted_posts();
DO $$
DECLARE
  v_action_id bigint;
BEGIN
  IF EXISTS (SELECT 1 FROM public.posts WHERE title = 'v1.8 purge reported post') THEN
    RAISE EXCEPTION 'Purge did not remove an expired post after its report was closed.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.reports r
    WHERE r.target_type = 'post'
      AND r.target_entity_id = current_setting('jc.test_purge_post_id')::bigint
      AND r.target_post_id IS NULL
      AND r.target_snapshot ->> 'title' = 'v1.8 purge reported post'
      AND r.status = 'resolved'
  ) THEN
    RAISE EXCEPTION 'Resolved report evidence was not retained after target purge.';
  END IF;

  BEGIN
    UPDATE public.reports
    SET target_snapshot = jsonb_build_object('type', 'post', 'id', current_setting('jc.test_purge_post_id')::bigint)
    WHERE target_type = 'post'
      AND target_entity_id = current_setting('jc.test_purge_post_id')::bigint;
    RAISE EXCEPTION 'Report evidence snapshot mutation unexpectedly succeeded.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  SELECT id INTO v_action_id
  FROM public.admin_actions
  ORDER BY id DESC
  LIMIT 1;

  BEGIN
    UPDATE public.admin_actions SET reason = 'tampered' WHERE id = v_action_id;
    RAISE EXCEPTION 'Audit row update unexpectedly succeeded.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    DELETE FROM public.admin_actions WHERE id = v_action_id;
    RAISE EXCEPTION 'Audit row delete unexpectedly succeeded.';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;

ROLLBACK;
