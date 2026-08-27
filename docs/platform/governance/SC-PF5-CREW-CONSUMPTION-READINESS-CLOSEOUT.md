# SC PF5 Crew Recommendation Consumption Readiness Closeout

## Document identity

| Field | Value |
|---|---|
| closeout ID | `sc-pf5-crew-consumption-readiness-closeout-v1` |
| status | `CLOSED / CLIENT_REPOSITORY_READY / NOT_DEPLOYED` |
| governing decision | `sc-pf5-crew-consumption-activation-v1` |
| backend authority main | `e6578e49e31b0c2c1242dbc0ff7cb252702187c1` |
| consuming repository | `gycha0109-beep/JC-FRONT` |
| consuming repository exact main | `577ec883269b1b7580049ed0f3a4264fdfe00d75` |
| canonical SQL tail | `62` |
| SQL `63+` | `UNALLOCATED` |
| deployment evidence | `NONE / NOT CLAIMED` |

## Closeout decision

PF5 client repository readiness is closed.

The first-party frontend repository now contains a verified implementation that consumes the authenticated Crew recommendation endpoint under the routing, ordering, exposure, failure and legacy-preservation constraints granted by `SC-PF5-CREW-CONSUMPTION-ACTIVATION.md`.

This closeout is intentionally narrower than deployment or production activation.

```text
CLIENT_REPOSITORY_READINESS=CLOSED
CLIENT_IMPLEMENTATION_MERGED=YES
PERSONALIZED_CREW_SCREEN_IMPLEMENTED=YES
LEGACY_SEARCH_FILTER_PATH_RETAINED=YES
MEMBERSHIP_ACTIONS_IMPLEMENTED=YES
DEPLOYED=NOT_CLAIMED
PRODUCTION_TRAFFIC=UNVERIFIED
BACKEND_RUNTIME_CHANGE=NONE
DB_CHANGE=NONE
SQL_ALLOCATION=NONE
SQL_63_PLUS=UNALLOCATED
```

## Frontend implementation evidence

| PR | Main SHA | Scope | Exact-head verification |
|---|---|---|---|
| `JC-FRONT #1` | `10b09eed959a8c845843bff220cdbb307183ea58` | PF5 Crew discovery routing/normalization foundation | Frontend CI `#10` SUCCESS |
| `JC-FRONT #2` | `2f3474480347660dcd5073424d419500c2b000ac` | `/api/v1` base compatibility repair | Frontend CI `#12` SUCCESS |
| `JC-FRONT #3` | `36827c48ffd84f9a4830215a96ddc6dba894a7bf` | stored-auth and API-base runtime service wiring | Frontend CI `#14` SUCCESS |
| `JC-FRONT #4` | `cee3da584e68ae50d8be63b8db68e6e0e000324d` | live Crew discovery screen vertical slice | Frontend CI `#16` SUCCESS |
| `JC-FRONT #5` | `577ec883269b1b7580049ed0f3a4264fdfe00d75` | join/cancel membership actions | Frontend CI `#18` SUCCESS |

The final consuming repository main is GitHub-verified and contains all predecessor slices.

## PF5 verification matrix

### 1. Authenticated default discovery selects the recommendation endpoint

Verified by the frontend service and contract tests.

The client reads the established `accessToken` key and routes an authenticated, no-filter, initial batch of at most 20 items to:

```text
GET /api/v1/recommendation/crews?limit=<batch>
```

No frontend proxy or second recommendation endpoint was introduced.

### 2. Anonymous discovery retains the legacy endpoint

Verified by contract tests and the live Crew screen consumption path.

Anonymous discovery uses:

```text
GET /api/v1/crews?page=<page>&size=<size>
```

No recommendation semantics are claimed for that response.

### 3. Keyword and region filtering retain legacy authority

Verified by routing tests and the live filter form.

Authenticated identity does not override this boundary. Any active keyword or region filter selects the legacy Crew list contract.

### 4. Recommendation order is preserved

The frontend normalization layer preserves the returned item order and exposes server rank/reason metadata without client-side score calculation, sorting or re-ranking.

No client ranking function exists in the consuming path.

### 5. Personalized failure is not silently replaced by legacy success

Verified by explicit single-request failure tests.

A failed personalized request is surfaced as an error. The client does not issue a hidden legacy request in the same failed path and does not label legacy newest-first content as recommendation output.

### 6. Personalized responses are not shared across identities

The consuming implementation has no personalized response cache.

Each Crew discovery request resolves the current stored authentication token and performs a fresh request. No cross-user cache key, singleton personalized result store, service-worker response cache, or shared recommendation response memoization was introduced.

### 7. Legacy discovery remains consumable

The client retains legacy response normalization, search/filter routing and pageable navigation. The implementation does not remove or reorder `/api/v1/crews`.

This is repository-level contract/build evidence, not a claim of a deployed end-to-end environment.

## API base and authentication compatibility

The implementation was aligned to the pre-existing Journey Connect frontend convention:

```text
VITE_API_BASE_URL=http://<host>/api/v1
ACCESS_TOKEN_STORAGE_KEY=accessToken
REFRESH_TOKEN_STORAGE_KEY=refreshToken
LOGIN_USER_STORAGE_KEY=loginUser
```

PF5 does not generate `/api/v1/api/v1/...` paths.

A `401` clears the stored authentication state and is not retried as a legacy Crew request inside the same operation.

## Membership action continuation

The consuming screen also activates the existing Crew membership operations:

```text
POST   /api/v1/crews/{crewId}/join
DELETE /api/v1/crews/{crewId}/join
```

Action availability is derived only from backend `CrewDtos.View.viewer` fields such as `canJoin`, `canCancel`, `owner` and `membershipStatus`.

This does not alter PF5 exposure semantics.

The backend remains authoritative for recommendation feedback. Existing `CrewRecommendationFeedbackAspect` records `crew-recommendation-feedback-v1 / approved_join` only after membership reaches `APPROVED`. A `PENDING` application is not treated as recommendation feedback, and the frontend does not write recommendation behavior events directly.

## Preserved authority boundaries

The following remain unchanged:

- `/api/v1/crews` legacy newest-first/search/filter/pageable authority;
- `crew_recommendation_exposure_v1` server-delivery exposure semantics;
- Recommendation P2 experiment authority;
- Search ranking and Search metric authority;
- recommendation behavior-event semantics;
- APP / RECOMMENDATION / ADMIN / AUTH database role boundaries;
- canonical SQL tail `62`;
- SQL `63+` unallocated state.

No backend runtime code, migration, role grant, exposure table or recommendation ranking policy is modified by this closeout.

## Production boundary

No deployment evidence was collected or required for this repository-readiness closeout.

Therefore the following claims are forbidden by this document:

- `DEPLOYED`;
- `PRODUCTION_ACTIVATED`;
- nonzero production recommendation traffic;
- viewport/client-render impression evidence;
- production reliability or latency claims.

If deployment or production traffic becomes relevant later, it requires target-environment evidence separate from this closeout.

## Final state

```text
PF5_GOVERNANCE_AUTHORITY=MERGED
PF5_CLIENT_IMPLEMENTATION=MERGED
PF5_CLIENT_REPOSITORY_READINESS=CLOSED
FRONTEND_MAIN=577ec883269b1b7580049ed0f3a4264fdfe00d75
DEPLOYMENT_STATUS=NOT_CLAIMED
BACKEND_MAIN_AT_CLOSEOUT_BASE=e6578e49e31b0c2c1242dbc0ff7cb252702187c1
DB_CHANGE=NONE
SQL_63_PLUS=UNALLOCATED
```

PF5 now has no remaining repository implementation blocker. Any next track must be separately allocated and must not infer SQL `63+`, production deployment, viewport tracking, experiment promotion, or legacy endpoint replacement from this closeout.
