# SC PF8 Comment Conversation Notifications Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf8-comment-conversation-notifications-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `922cd571322f29d8f4239db320a66f9dac29d638` |
| product slice | `JC-PF8 Comment Conversation Notifications` |
| donor references | `YTAK99/Journey-Connect PR #19`, `YTAK99/Journey-Connect PR #22` |
| canonical SQL tail | `66` |
| runtime owner | `APP` |

## Decision

PF8 allocates one bounded notification extension on top of the already-merged PF2 notification inbox and PF7 one-depth comment reply model.

The product contract is:

```text
CONTRACT_ID=comment-conversation-notification-v1
TOP_LEVEL_COMMENT_EVENT=post_comment
TOP_LEVEL_COMMENT_RECIPIENT=post.author_id
REPLY_EVENT=comment_reply
REPLY_RECIPIENT=parent_comment.author_id
TARGET_TYPE=post
TARGET_ID=post.id
SELF_NOTIFICATION=SUPPRESS
TOP_LEVEL_DEDUPE_KEY=post_comment:{commentId}
REPLY_DEDUPE_KEY=comment_reply:{replyCommentId}
WRITE_ROLE=APP
COMMENT_NOTIFICATION_CONSISTENCY=SAME_APP_TRANSACTION
NEW_SQL=NONE
SQL_67_PLUS=UNALLOCATED
```

PF8 does not allocate post-like notifications, bookmark notifications, report-result notifications, push notifications, email, WebSocket, SSE, arbitrary thread subscriptions, mention parsing, recommendation feedback, ranking signals, search signals, or deployment.

## Existing authority reused

PF8 reuses the existing canonical `public.user_notifications` storage and APP-role notification write/read authority established by PF2. It does not allocate a second notification table, a second inbox, or additional database grants.

The existing notification APIs remain authoritative and unchanged:

```text
GET   /api/v1/notifications
GET   /api/v1/notifications/unread-count
PATCH /api/v1/notifications/{notificationId}/read
PATCH /api/v1/notifications/read-all
```

PF8 adds producer behavior only. It does not alter notification pagination, read ownership, unread-count semantics, actor projection, target projection, or mark-read authorization.

## Top-level comment notification

When an authenticated active user successfully creates a top-level comment on a currently commentable published post:

- the recipient is the post author;
- the actor is the comment author;
- the notification type is `post_comment`;
- `target_type = post`;
- `target_id = post.id`;
- the dedupe key is `post_comment:{commentId}`;
- if actor and recipient are the same user, no notification is created.

The notification is emitted only for a successfully persisted comment. Failed or rejected comment creation must not create a notification.

## Reply notification

When an authenticated active user successfully creates a PF7-valid reply:

- the recipient is the visible top-level parent comment author;
- the actor is the reply author;
- the notification type is `comment_reply`;
- `target_type = post`;
- `target_id = post.id`;
- the dedupe key is `comment_reply:{replyCommentId}`;
- if actor and recipient are the same user, no notification is created.

The reply does not additionally generate a `post_comment` notification to the post author. One successful comment write produces at most one PF8 notification event.

PF7 parent validity remains authoritative. Missing, cross-post, reply-to-reply, author-deleted, moderation-deleted, or inactive-author parent comments are still rejected before a reply notification can be produced.

## Transaction boundary

Comment persistence and its PF8 notification write must participate in the same APP-role transaction.

Required behavior:

- invalid comment/reply -> no comment row and no notification row;
- notification storage failure -> the comment/reply write must not be committed independently;
- dedupe conflict for the exact notification event is handled by the existing unique `dedupe_key` / `ON CONFLICT DO NOTHING` behavior and is not treated as a second notification;
- no cross-role transaction switch is introduced.

PF8 does not authorize asynchronous eventual delivery, an outbox, background worker, or fail-open notification loss for comment creation.

## Runtime change allocation

The successor implementation may change only the narrow runtime surfaces needed for this producer behavior:

- `jc-backend/src/main/java/com/jc/backend/notification/NotificationService.java`
- `jc-backend/src/main/java/com/jc/backend/post/CommentReplyService.java`
- dedicated PF8 tests

Changes to `PostController`, `PostDtos`, `RecommendationPostInteractionService`, Recommendation persistence, Search runtime, Admin runtime, SecurityConfig, or PF7 SQL are not allocated by this decision.

If implementation discovers that one of those protected surfaces is genuinely required, a new governance decision is required before changing it.

## Database / SQL boundary

PF8 allocates no migration and no canonical SQL file.

- SQL `65` and `66` remain the PF7 comment-reply files already merged.
- SQL `67+` remains unallocated.
- existing `user_notifications` schema and APP grants remain unchanged.
- no role grant widening is permitted.
- no trigger or stored procedure is allocated.

## Recommendation / Search boundary

Comment notifications are presentation-side social events. They are not recommendation behavior evidence.

PF8 must not modify or consume:

- `RecommendationPostInteractionService`;
- `recommendation_behavior_event` semantics;
- recommendation exposure tables or contracts;
- Crew recommendation feedback;
- Explore/Search ranking, shadow, exposure, CTR, or cutover authority.

No `post_comment` or `comment_reply` notification may be silently treated as a recommendation score, feedback, impression, click, save, join, or search event.

## Donor adaptation rule

Team repository code is reference-only.

The donor implementation demonstrates useful product semantics (`post_comment` and `comment_reply`) but is not authoritative for personal-repository transaction annotations, table names, database roles, protected-source policy, or PF7 parent validation. The successor implementation must use the personal repository's `DatabaseTransactional(role = DatabaseRole.APP)` authority and current `public.user_notifications` schema.

## Required verification

The successor implementation must cover at minimum:

1. top-level comment by another user creates exactly one `post_comment` notification for the post author;
2. self-comment creates no notification;
3. valid reply creates exactly one `comment_reply` notification for the parent author;
4. self-reply creates no notification;
5. a reply does not additionally notify the post author as `post_comment`;
6. invalid PF7 parent cases create no notification;
7. notification `target_type`, `target_id`, actor, and dedupe identity are exact;
8. existing Crew notification producer behavior remains unchanged;
9. notification inbox/read/unread ownership remains unchanged;
10. no SQL67+ file, DB grant expansion, Recommendation mutation, Search mutation, or deployment claim appears.

The implementation must pass the repository's canonical PostgreSQL 15/18 integration gates and inherited Backend/P0/Admin/PIE protected regression gates on one exact head before merge.

## Explicit non-goals

PF8 does not authorize:

- post like notification;
- bookmark notification;
- report-created or report-result notification;
- notification aggregation/count coalescing by actor;
- mention notification;
- arbitrary-depth thread notification;
- subscription/watch models;
- push/email/SMS delivery;
- WebSocket/SSE;
- Recommendation/Search feedback;
- frontend work;
- deployment or production traffic changes;
- SQL `67+`.
