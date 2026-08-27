# SC PF8 Comment Conversation Notifications SQL Amendment

## Identity

| Field | Value |
|---|---|
| amendment ID | `sc-pf8-comment-notification-sql-amendment-v1` |
| status | `APPROVED / CORRECTIVE_ALLOCATION` |
| amends | `sc-pf8-comment-conversation-notifications-v1` |
| discovered against main | `32af43135c35414f40c97b9b01afe23343529f78` |
| prior canonical SQL tail | `66` |
| newly allocated SQL | `67`, `68` |

## Why this amendment exists

The initial PF8 allocation stated that no new SQL was required because it correctly reused the existing `public.user_notifications` table and APP privileges, but it did not inspect the complete persistence domain encoded by canonical SQL55.

Canonical SQL55 contains both:

- `user_notifications_type_check`, which admits only `crew_application`, `crew_approved`, and `crew_rejected`;
- `user_notifications_target_type_check`, which requires `target_type = 'crew'`.

Therefore the allocated PF8 runtime events `post_comment` and `comment_reply` with `target_type = 'post'` cannot be persisted under the current canonical schema. Runtime-only implementation would fail closed at PostgreSQL and would contradict the allocation's own requirement that a successfully persisted comment and its notification share one APP transaction.

This is a contract/schema compatibility correction, not a product-scope expansion.

## Corrective allocation

PF8 now allocates exactly two successor SQL files:

### SQL67

`67_comment_conversation_notification_types.sql`

It may alter only the existing notification CHECK domain so that the canonical accepted pairs are:

```text
crew_application -> crew
crew_approved    -> crew
crew_rejected    -> crew
post_comment     -> post
comment_reply    -> post
```

The implementation must reject cross-domain pairs and unknown types.

SQL67 must not:

- add or remove notification columns;
- create another notification table;
- alter indexes or dedupe uniqueness;
- alter `read_at` semantics;
- alter sequence ownership or sequence privileges;
- grant or revoke runtime role privileges;
- create triggers, procedures, functions, views, or materialized views;
- add Recommendation/Search/Exposure semantics.

### SQL68

`68_comment_conversation_notification_types_smoke_test.sql`

It must verify on PostgreSQL 15 and 18:

- all three PF2 Crew type/target pairs still succeed;
- both PF8 post type/target pairs succeed;
- `post_comment/crew` fails;
- `comment_reply/crew` fails;
- `crew_application/post` fails;
- unknown types fail;
- existing APP read/insert/read-state authority remains unchanged;
- AUTH, ADMIN, RECOMMENDATION and PUBLIC do not gain notification runtime authority;
- all test data is rollback-only.

## Unchanged authority

Everything else in the PF8 allocation remains unchanged:

- top-level comment recipient = post author;
- reply recipient = parent comment author;
- self notification suppressed;
- one comment write produces at most one PF8 notification;
- same APP transaction consistency;
- existing inbox/read/unread APIs unchanged;
- no post-like, bookmark, report-result, mention, push, WebSocket, SSE, Recommendation, Search, frontend, deployment, or production-traffic authority.

SQL69+ remains unallocated.

## Implementation gate

PF8 runtime implementation must not begin from the original allocation merge main. It must begin from the main revision produced by merging this corrective amendment, and SQL67/68 plus their canonical test mirrors must be included in the same successor implementation PR as the bounded runtime changes.
