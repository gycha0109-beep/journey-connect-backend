# SC PF7 Comment Replies Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf7-comment-replies-allocation-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `6bc7522d7ff9abd00e9c474e2eef8bd0aa0b964d` |
| product slice | `JC-PF7 Comment Replies` |
| donor reference | `YTAK99/Journey-Connect PR #22` |
| canonical SQL tail before allocation | `64` |
| runtime owner | `APP` |

## Decision

PF7 allocates one narrow, one-depth reply capability on top of the current canonical comment authority.

The feature extends an existing comment with one optional parent reference. A comment with no parent remains a top-level comment. A comment with a parent is a reply. Replies may target top-level comments only; replies to replies are forbidden.

PF7 does not allocate arbitrary-depth threads, notifications, mentions, reactions, direct messages, chat transport, ranking signals, recommendation feedback, search indexing, or deployment.

The authoritative product contract is:

```text
CONTRACT_ID=comment-replies-v1
ENTITY=comment
STORAGE_FIELD=comments.parent_comment_id
VALUE_STATE=NULL_OR_VISIBLE_TOP_LEVEL_COMMENT_ID
MAX_DEPTH=1
PARENT_SAME_POST=YES
PARENT_VISIBLE_AT_CREATE=YES
AUTHENTICATED_CREATE=YES
READ_SHAPE=FLAT_PAGE_WITH_PARENT_COMMENT_ID
NOTIFICATION_FEATURE=NO
RECOMMENDATION_FEATURE=NO
SEARCH_FEATURE=NO
DEPLOYMENT=NOT_CLAIMED
```

## API compatibility boundary

PF7 preserves the existing comment collection endpoints and extends their payloads minimally:

```text
POST /api/v1/posts/{postId}/comments
GET  /api/v1/posts/{postId}/comments
DELETE /api/v1/comments/{commentId}
```

`PostDtos.CommentRequest` may add nullable `parentCommentId` while preserving existing content-only callers.

`PostDtos.CommentView` may add nullable `parentCommentId` so clients can identify replies without replacing the existing flat paginated read model with a nested tree response.

A request with `parentCommentId = null` creates the same top-level comment represented by the pre-PF7 contract.

PF7 does not authorize a second comment ownership model, a second delete endpoint, or a separate reply table.

## Parent validity boundary

When `parentCommentId` is supplied, creation is authorized only when the referenced parent:

- exists;
- belongs to the same `postId` as the new reply;
- is a top-level comment whose own `parent_comment_id` is `NULL`;
- is not author-deleted;
- is not moderation-deleted;
- belongs to a post that is currently writable through the existing published-post comment policy.

A missing, author-deleted, or moderation-deleted parent is treated as unavailable and must not become a valid reply target.

Cross-post parents and replies-to-replies are invalid parent relationships and must be rejected. The implementation must expose stable domain errors and must not silently coerce an invalid reply into a top-level comment.

The donor implementation's plain parent `findById()` behavior is not authoritative because it can resolve a parent that current visibility policy would no longer admit as a reply target.

## Lifecycle boundary

PF7 preserves the existing soft-delete and moderation lifecycle for each comment row.

Creating a reply does not transfer ownership from the reply author to the parent author or post author. Existing author-only comment deletion remains authoritative for user deletion, and existing moderation authority remains authoritative for moderation deletion.

PF7 does not allocate cascading user deletion of replies when a parent is later soft-deleted. A reply remains its own comment record and retains its `parent_comment_id`; client presentation of a later-unavailable parent is outside this allocation except that no new reply may target an already unavailable parent.

Physical hard-delete behavior remains database-maintenance territory and must not be exposed as a product API.

## SQL allocation

### SQL 65

`65_comment_replies.sql`

Allocated responsibility:

- add nullable `parent_comment_id BIGINT` to canonical `comments`;
- add a self-referential foreign key to canonical `comments(id)`;
- add only the index/constraint support needed for parent lookup and integrity;
- preserve all existing comment columns and lifecycle fields;
- preserve existing APP comment read/write authority without widening AUTH, ADMIN, RECOMMENDATION, SEARCH or unrelated role privileges;
- do not create a second comments/replies table;
- do not add notification, mention, reaction, recommendation, search or exposure persistence.

SQL 65 may enforce structural invariants at database level where practical, but application validation remains responsible for the product rule that a newly selected parent is currently visible and writable under the existing comment policy.

### SQL 66

`66_comment_replies_smoke_test.sql`

Allocated responsibility:

- PostgreSQL 15/18 column/type/nullability verification;
- self-reference foreign-key verification;
- structural verification for the one-depth/same-post contract implemented by SQL 65;
- verification that existing top-level comments remain valid with `parent_comment_id IS NULL`;
- verification that APP retains only the comment authority needed by the existing runtime plus PF7;
- verification that no recommendation/search/exposure schema or privilege authority is introduced.

SQL `67+` remains unallocated by this decision.

## Read boundary

The existing post comment read remains a flat paginated collection. PF7 adds parent identity; it does not allocate a nested materialized thread API or a new pagination model.

Visible reply rows follow the existing comment visibility rules applied to the reply itself. The implementation must not broaden public post visibility, draft visibility, author-account visibility, or moderation visibility merely because a row is a reply.

PF7 does not authorize fetching comments from another post in order to fill a page, client-visible hidden-comment payloads, or disclosure of moderation-only data.

## Write boundary

Reply creation uses the same authenticated active-user and published-post comment authority as existing top-level comment creation.

The implementation must reject:

- a parent from another post;
- a parent that is itself a reply;
- an author-deleted parent;
- a moderation-deleted parent;
- a missing parent;
- any attempt to create a reply against a post that the existing comment creation policy would reject.

No reply-specific privilege is granted to the parent author, post author, recommendation runtime, search runtime, or anonymous callers.

## Recommendation, exposure and search boundary

PF7 is not a recommendation, exposure, feedback, ranking or search feature.

It must not alter:

- recommendation candidate retrieval;
- recommendation eligibility, score, ordering or reason codes;
- recommendation interaction semantics for LIKE/UNLIKE/SAVE/UNSAVE;
- recommendation exposure identity or persistence;
- Explore/search shadow contracts;
- public post eligibility or ordering;
- Crew recommendation or Crew exposure authorities.

`parent_comment_id` must not become a recommendation or search feature merely because it is available on a comment row.

## Protected-source boundary

The successor implementation PR may change only the minimum post/comment sources required to implement the approved PF7 contract.

If a required existing source is governed by an exact-hash or controlled-delta test, PF7 does not authorize bypassing that governance. The successor must either:

1. implement through an unprotected bounded extension while preserving the protected source; or
2. add an explicit, narrowly scoped governance delta through the repository's existing protected-change mechanism and prove the full protected readiness gate.

Broad allowlist expansion, disabling a static gate, replacing exact-hash protection with a weaker assertion, or treating this allocation document as permission to modify unrelated protected sources is forbidden.

## Donor provenance

`YTAK99/Journey-Connect` PR #22 is donor/reference material for the product idea and one-depth parent shape only.

The personal repository must adapt the feature to its canonical `comments` table, APP transaction boundary, current soft-delete/moderation lifecycle, canonical SQL sequence, database role model and protected-source governance. Donor Flyway numbering, table names, notification additions and plain parent lookup behavior are not authoritative here.

## Completion gate

Implementation is complete only after a successor implementation PR from the merged PF7 allocation main proves at minimum:

- existing content-only comment creation still creates a top-level comment;
- authenticated active user can reply to a visible top-level comment on the same published post;
- response/read model exposes the correct nullable `parentCommentId`;
- cross-post parent is rejected;
- reply-to-reply is rejected;
- missing parent is rejected;
- author-deleted parent is rejected;
- moderation-deleted parent is rejected;
- existing comment author deletion semantics remain intact for top-level comments and replies;
- existing moderation semantics remain intact for top-level comments and replies;
- canonical SQL 65/66 copies are byte-identical where required;
- PostgreSQL 15/18 canonical integration passes;
- Backend protected regression passes;
- Recommendation/P0/Admin/PIE successor gates remain green;
- no recommendation, exposure, search, notification or deployment authority is added.

Until exact-head verification passes, implementation state is `IMPLEMENTED_UNVERIFIED` and no merge is authorized.

## Non-goals

This allocation does not authorize:

- arbitrary-depth comment trees;
- reply pagination separate from the existing comment page;
- notifications or push delivery;
- @mentions;
- comment reactions or votes;
- direct messages, WebSocket, SSE or chat;
- ranking/recommendation/search features derived from replies;
- frontend implementation;
- deployment or production activation;
- any SQL after 66.
