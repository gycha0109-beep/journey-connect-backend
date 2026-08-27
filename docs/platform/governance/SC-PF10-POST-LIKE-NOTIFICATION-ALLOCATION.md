# SC PF10 Post Like Notification Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf10-post-like-notification-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `31bb35504e3d69a82691aaaad2ede6c578c07931` |
| product slice | `JC-PF10 Post Like Notification` |
| donor reference | `YTAK99/Journey-Connect PR #19` |
| canonical SQL tail | `68` |
| allocated successor SQL | `69`, `70` |
| runtime owner | `APP` |

## Decision

PF10 allocates one bounded notification producer on the existing authenticated post-like API. It does not add or change an HTTP endpoint.

```text
CONTRACT_ID=post-like-notification-v1
SOURCE_ENDPOINT=POST /api/v1/posts/{postId}/likes
SOURCE_ACTION=LIKE
SOURCE_RESULT=APPLIED
NOTIFICATION_TYPE=post_like
TARGET_TYPE=post
WRITE_ROLE=APP
DEDUPE_KEY=post_like:{postId}:{actorId}
SELF_NOTIFICATION=SUPPRESSED
TRANSACTION=LIKE_STATE_AND_BEHAVIOR_EVENT_AND_NOTIFICATION_ATOMIC
SQL_69=ALLOCATED
SQL_70=ALLOCATED
SQL_71_PLUS=UNALLOCATED
```

A notification is created only when the canonical recommendation interaction reports a real `LIKE` transition as `Result.APPLIED`. `DUPLICATE`, `NO_CHANGE`, `IDEMPOTENCY_CONFLICT`, `UNLIKE`, `SAVE`, and `UNSAVE` do not create a post-like notification.

## Existing interaction authority retained

The current public like endpoint already delegates to `RecommendationPostInteractionService`, which creates the canonical interaction payload and delegates the state/event write to `RecommendationPostInteractionStore` and `public.apply_recommendation_post_interaction(...)`.

PF10 must not create a second like writer, second recommendation event writer, alternate idempotency policy, or direct `post_likes` write path.

The recommendation interaction result remains authoritative:

- `APPLIED` means a state transition was committed by the canonical command;
- `DUPLICATE` and `NO_CHANGE` are not new user-visible like transitions;
- `IDEMPOTENCY_CONFLICT` remains the existing conflict outcome.

PF10 may expose this already-produced result through a compatibility-preserving application method solely so the notification coordinator can decide whether a notification is eligible. Existing callers that do not need the result must retain their current behavior.

No scoring, ranking, recommendation profile, canonical payload, behavior-event schema, exposure, impression, session, run binding, or idempotency semantics may change.

## Coordinator boundary

PF10 allocates a narrow APP-role coordinator around the existing LIKE interaction path.

The intended transaction is:

```text
PostController.like
  -> PostLikeNotificationCoordinator [APP transaction]
      -> RecommendationPostInteractionService
          -> RecommendationPostInteractionStore [joins same APP transaction]
      -> if result == APPLIED
           resolve exact post author
           -> NotificationService.postLiked [joins same APP transaction]
```

The coordinator is responsible only for orchestration. It must not duplicate post visibility or interaction validation already enforced by the canonical recommendation command.

The post author lookup may occur only after an `APPLIED` result and exists solely to identify the notification recipient. A missing author/post after an `APPLIED` transition is an internal invariant violation, not a second visibility contract.

## Atomicity

The like state transition, recommendation behavior event, and eligible notification insert must participate in one APP-role transaction.

Therefore:

- if the canonical interaction fails, no notification exists;
- if the notification insert fails, the like state transition and recommendation behavior event roll back;
- notification creation must not be deferred to an unrelated transaction, asynchronous worker, after-commit callback, WebSocket, or external broker in PF10.

This atomicity requirement is specific to PF10 persistence. It does not authorize real-time delivery.

## Notification semantics

The notification row uses:

```text
type=post_like
target_type=post
target_id={postId}
actor_id={likingUserId}
recipient_id={postAuthorId}
dedupe_key=post_like:{postId}:{actorId}
```

Self-like notifications are suppressed.

The dedupe key is intentionally stable for the actor/post pair. An unlike followed by a later re-like does not create a second notification row for the same actor/post pair. This is an anti-noise inbox rule and does not alter canonical like state or recommendation behavior-event semantics.

The existing notification list/unread/read/read-all API is reused unchanged.

## SQL allocation

PF10 allocates exactly two successor SQL files.

### SQL69

`69_post_like_notification_type.sql`

SQL69 may only widen the existing `public.user_notifications` type/target CHECK domain so the valid pairs become:

- `crew_application -> crew`
- `crew_approved -> crew`
- `crew_rejected -> crew`
- `post_comment -> post`
- `comment_reply -> post`
- `post_like -> post`

SQL69 must not create or alter any table column, index, sequence, function, procedure, trigger, role, or grant. It must not weaken existing pair validation.

### SQL70

`70_post_like_notification_type_smoke_test.sql`

SQL70 is verification-only and must prove at minimum:

- `post_like -> post` is accepted;
- `post_like -> crew` is rejected;
- all PF8/PF2 valid pairs remain accepted;
- invalid cross-target pairs remain rejected;
- inherited APP/AUTH/ADMIN/RECOMMENDATION notification privileges remain unchanged.

Production SQL and canonical test-resource mirrors must remain byte-identical. Canonical bootstrap advances through SQL70 only.

SQL `71+` remains unallocated.

## Runtime allocation

The successor implementation may modify only the minimum equivalent of:

- add `PostLikeNotificationCoordinator`;
- add `NotificationService.postLiked(...)` producer support;
- add a compatibility-preserving result-returning path in `RecommendationPostInteractionService`;
- change only `PostController.like(...)` wiring to use the coordinator;
- add SQL69/70, canonical mirrors, and bootstrap references;
- add dedicated PF10 integration/static verification.

No modification is allocated for:

- `RecommendationPostInteractionStore` or its PostgreSQL command;
- Recommendation scoring/ranking/profile/exposure/impression code;
- bookmark/save notification behavior;
- comment/reply/crew notification semantics;
- notification inbox/read API response shape;
- Admin report runtime;
- report-resolution notification;
- Search runtime;
- `SecurityConfig`;
- Auth runtime;
- frontend.

If any excluded surface is genuinely required, implementation must stop and obtain a new governance decision.

## Donor adaptation rule

`YTAK99/Journey-Connect` PR #19 is product-semantic reference only. Its `PostService.like()` notification hook predates the personal repository's canonical recommendation interaction path and must not be copied as authority.

The personal repository's `RecommendationPostInteractionService` / `RecommendationPostInteractionStore` / `public.apply_recommendation_post_interaction(...)` lineage is authoritative for like state and recommendation evidence.

## Required verification

The successor implementation must verify at minimum:

1. a first successful non-self LIKE creates exactly one `post_like` notification;
2. recipient is the exact post author and actor is the liking user;
3. target type/id are exactly `post` / post ID;
4. dedupe key is exactly `post_like:{postId}:{actorId}`;
5. self LIKE changes like state normally but creates no notification;
6. duplicate/no-change LIKE does not create another notification;
7. unlike creates no notification;
8. unlike then re-like does not create a second inbox row for the same actor/post pair;
9. SAVE/UNSAVE behavior remains notification-free;
10. existing recommendation canonical payload/event/idempotency behavior remains unchanged;
11. forced notification persistence failure rolls back both post-like state and recommendation behavior event;
12. existing comment/reply/crew notification tests remain green;
13. SQL69/70 production/mirror files are byte-identical;
14. SQL69 changes only the existing CHECK domain;
15. APP/AUTH/ADMIN/RECOMMENDATION role privileges remain unchanged;
16. PostgreSQL 15 and 18 canonical suites pass;
17. SQL71+ remains absent.

The implementation must pass inherited Backend/P0/Admin/PIE protected regression gates on one exact head before merge.

## Explicit non-goals

PF10 does not authorize:

- report-resolution notifications;
- bookmark/save notifications;
- follow/DM notifications;
- notification aggregation;
- notification deletion;
- push/email/SMS;
- WebSocket/SSE;
- Google login or external identity;
- Recommendation/Search signal changes beyond the already-existing LIKE event;
- frontend work;
- deployment or production traffic activation;
- SQL `71+`.
