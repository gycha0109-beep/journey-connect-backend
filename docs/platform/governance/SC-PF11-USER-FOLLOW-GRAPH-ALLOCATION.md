# SC PF11 User Follow Graph Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf11-user-follow-graph-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `26191ba91cfd4f8cd6b86a02e9bf81b72c9f7792` |
| product slice | `JC-PF11 User Follow Graph` |
| canonical SQL tail | `70` |
| allocated successor SQL | `NONE` |
| runtime owner | `APP` |

## Decision

PF11 allocates the authenticated mutation API for the already-canonical directed user-follow relation. It does not allocate a new table, migration, notification producer, follower/following read surface, or recommendation signal.

```text
CONTRACT_ID=user-follow-graph-v1
FOLLOW_ENDPOINT=POST /api/v1/users/{userId}/follow
UNFOLLOW_ENDPOINT=DELETE /api/v1/users/{userId}/follow
HTTP_STATUS=204
AUTHENTICATION=REQUIRED
ACTOR_SOURCE=VERIFIED_JWT_USER_ID
TARGET_USER=ACTIVE_ONLY
SELF_FOLLOW=REJECTED
FOLLOW_CREATE=IDEMPOTENT
UNFOLLOW_DELETE=IDEMPOTENT
STORAGE=public.follows
WRITE_ROLE=APP
SQL_71_PLUS=UNALLOCATED
```

The existing `public.follows` row is the only relationship authority. A row `(follower_id, following_id)` means the follower currently follows the target. Absence of that row means no follow relationship exists.

## Existing database authority retained

The canonical base schema already defines:

```sql
CREATE TABLE public.follows (
  follower_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  following_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, following_id),
  CONSTRAINT follows_not_self_check CHECK (follower_id <> following_id)
);
```

The canonical security migration already grants `jc_app` `SELECT`, `INSERT`, and `DELETE` on `public.follows`. `jc_auth` does not own this application relationship mutation. `jc_admin` remains read-only for this relation through its existing operating-data access. No sequence is required because the relation uses a composite primary key.

PF11 must reuse those existing schema and privilege contracts unchanged.

No SQL file is required to implement PF11. SQL `71+` remains unallocated.

## Existing visibility consequence retained

`public.can_user_view_post(...)` already consumes `public.follows` when evaluating posts whose visibility is `followers`.

PF11 does not create a second visibility rule. A successful follow mutation naturally changes the result of that already-existing visibility predicate because the canonical relation row now exists. A successful unfollow naturally removes that relationship input.

PF11 must not alter:

- post visibility enum values;
- `public.can_user_view_post(...)`;
- post publication/moderation rules;
- public-profile serialization;
- feed ranking or recommendation eligibility.

## HTTP mutation semantics

### Follow

`POST /api/v1/users/{userId}/follow`

The authenticated actor is derived only from the verified JWT identity. Client input cannot select or override `follower_id`.

The target `{userId}` must resolve to an active application user. The actor must also remain an active application user under the existing common user-status policy.

Following oneself is rejected before persistence with the stable application error code:

```text
FOLLOW_SELF_NOT_ALLOWED
```

A valid first follow creates exactly one canonical `(actorUserId, targetUserId)` row.

Repeating the same valid follow is idempotent: it returns the same `204 No Content` API success and does not create another row or timestamp-bearing duplicate relationship.

### Unfollow

`DELETE /api/v1/users/{userId}/follow`

The actor and target identity rules are the same as follow. Self-target remains invalid rather than becoming a silent no-op.

If the canonical relationship exists, it is deleted. If it does not exist, the operation is idempotent and still returns `204 No Content`.

The mutation is a physical delete of a relationship edge, not deletion of user or content evidence. Existing physical-delete restrictions on posts/comments do not apply to this dedicated relation table, for which `jc_app` already has `DELETE` authority.

## Runtime allocation

The successor implementation may add only the minimum equivalent of:

- a dedicated `UserFollowService` under the user application boundary;
- a narrow follow persistence adapter/repository over existing `public.follows`;
- `POST /api/v1/users/{userId}/follow` wiring in `UserController`;
- `DELETE /api/v1/users/{userId}/follow` wiring in `UserController`;
- dedicated PF11 integration/static verification.

The implementation should keep `UserService` focused on profile/interests and must not require a new response DTO because both mutation endpoints return `204 No Content`.

No change is allocated for:

- `UserDtos.PublicProfile` or `UserDtos.UserProfile`;
- `SecurityConfig`;
- canonical SQL production files or canonical SQL mirrors;
- `public.follows` schema, indexes, constraints, grants, or foreign keys;
- notification type/domain or `NotificationService`;
- Recommendation scoring/ranking/profile/behavior/exposure/impression code;
- Search runtime;
- Admin runtime;
- Auth runtime;
- frontend.

If an excluded surface is genuinely required, implementation must stop and obtain a new governance decision.

## SOC sequencing boundary

PF11 implements only the relationship mutation core corresponding to the User Follow Graph slice.

The following adjacent social features remain separate future authority:

- followers/following list APIs and pagination;
- follower/following counters on public profiles;
- follow-state enrichment of public-profile responses;
- follow notifications;
- social-graph recommendation or behavioral signals;
- user discovery/search based on the follow graph;
- blocks, mutes, close-friends, follow requests, or private-account approval flows.

PF11 must not pre-implement those surfaces.

## Required verification

The successor implementation must verify at minimum:

1. an authenticated active user can follow a different active user;
2. the stored row is exactly `(follower_id = actor, following_id = target)`;
3. duplicate follow returns `204` and preserves exactly one row;
4. an authenticated active user can unfollow an existing relation;
5. unfollow of a missing relation returns `204` and remains absent;
6. self-follow is rejected with `FOLLOW_SELF_NOT_ALLOWED` and writes no row;
7. self-unfollow is rejected with the same self-target policy and writes no row;
8. an inactive or unavailable target cannot receive a new follow;
9. an inactive/unavailable actor cannot mutate follow state under the common active-user policy;
10. caller-supplied path/body data cannot forge `follower_id`;
11. the existing `public.follows` composite primary key and self-check remain unchanged;
12. inherited `jc_app` `SELECT/INSERT/DELETE` authority on `public.follows` remains unchanged;
13. `jc_auth`, `jc_admin`, and Recommendation roles receive no new follow mutation privilege;
14. existing follower-only post visibility continues to consume the same canonical `public.follows` relation;
15. no notification row or new notification type is created by follow/unfollow;
16. no recommendation behavior/exposure row is created by follow/unfollow;
17. production and canonical SQL directories remain unchanged through SQL70;
18. SQL71+ remains absent;
19. PostgreSQL 15 and 18 inherited canonical suites remain green;
20. inherited Backend/P0/Admin/PIE protected regression gates pass on one exact implementation head.

## Explicit non-goals

PF11 does not authorize:

- follower/following list endpoints;
- follower/following counts;
- public-profile follow-state fields;
- follow notifications;
- notification aggregation;
- social-graph recommendation features;
- new recommendation behavior events;
- Search indexing or user search;
- private-account follow requests;
- blocking or muting;
- WebSocket/SSE/push/email/SMS;
- frontend work;
- deployment or production traffic activation;
- SQL `71+`.
