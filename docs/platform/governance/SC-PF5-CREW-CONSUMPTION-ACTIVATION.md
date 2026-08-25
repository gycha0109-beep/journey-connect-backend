# SC PF5 Crew Recommendation Consumption Activation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf5-crew-consumption-activation-v1` |
| status | `APPROVED / CLIENT_CONSUMPTION_AUTHORITY_GRANTED` |
| prerequisite main | `70e2b36df6b1c5b7d8623e8da4689fc0b024c364` |
| prerequisite read path | `JC-PF4 Crew Intelligence read path / PR #81` |
| prerequisite exposure authority | `crew_recommendation_exposure_v1 / PR #84` |
| governing recommendation contract | `crew-recommendation-contract-v1` |
| recommendation endpoint | `/api/v1/recommendation/crews` |
| legacy Crew endpoint | `/api/v1/crews` |
| canonical SQL tail | `62` |

## Decision

System Coordination authorizes first-party client consumption of the already-separated authenticated Crew recommendation endpoint for the default personalized Crew discovery batch.

This decision is a **client routing and consumption authority**. It does not replace the backend legacy Crew list authority, does not remove the legacy newest-first path, and does not allocate any new database object.

The activated routing contract is:

```text
CONTRACT_ID=sc-pf5-crew-consumption-activation-v1
PERSONALIZED_ENDPOINT=/api/v1/recommendation/crews
LEGACY_ENDPOINT=/api/v1/crews
PERSONALIZED_REQUIRES_AUTHENTICATION=YES
PERSONALIZED_DEFAULT_BATCH_MAX=20
CLIENT_SIDE_RESORTING=FORBIDDEN
CLIENT_SIDE_RESCORE=FORBIDDEN
SERVER_DELIVERY_EXPOSURE=crew_recommendation_exposure_v1
VIEWPORT_IMPRESSION_SEMANTIC=NO
LEGACY_ENDPOINT_REMOVAL=NO
BACKEND_LEGACY_ORDERING_REPLACEMENT=NO
SQL_ALLOCATION=NONE
SQL_63_PLUS=UNALLOCATED
```

## Authorized client routing

The first-party client may select `/api/v1/recommendation/crews` only when all of the following are true:

1. the viewer has a resolved authenticated identity;
2. the Crew surface is the default discovery surface;
3. no keyword search is active;
4. no region filter is active;
5. the requested personalized batch is the initial batch and does not exceed `20` items.

The client must use the legacy `/api/v1/crews` authority when any of the following are true:

- the viewer is anonymous;
- keyword search is active;
- region filtering is active;
- the request requires pageable legacy list semantics beyond the initial personalized batch;
- another existing Crew flow explicitly depends on the legacy pageable contract.

This decision does not authorize emulating unsupported recommendation pagination on the client.

## Failure and fallback semantics

The personalized endpoint remains fail-closed with respect to `crew_recommendation_exposure_v1` persistence.

Therefore:

- a non-success personalized response must not be re-labelled as a successful personalized delivery;
- the client must not silently issue a legacy request inside the same failed personalized request path and present the result as though recommendation succeeded;
- after authentication state is explicitly resolved as anonymous, the client may use the normal legacy route;
- a user-visible explicit legacy escape such as `최신순으로 보기` may be implemented separately, but its result is non-personalized legacy content and must not be represented as Crew recommendation output;
- client retry logic must not synthesize, mutate, or infer Crew exposure evidence.

The server-side `server_delivery_commit_v1` semantic remains authoritative. A successful response proves durable server delivery evidence only; it does not prove render, viewport entry, or conscious user observation.

## Response consumption contract

For `/api/v1/recommendation/crews`, the client must:

- preserve server item order exactly;
- use `item.crew` as the Crew card presentation authority;
- treat `rank` as the server absolute rank for the returned batch;
- treat `score` as diagnostic/display metadata only, not as a client ranking input;
- render recommendation reasons only from returned reason codes/contributions when such UI is enabled;
- not recompute ranking reasons from Crew card fields;
- not merge recommendation items with legacy items and re-rank the combined set;
- not cache a personalized response across authenticated users.

The client may omit score/reason presentation entirely without changing ranking semantics.

## Legacy authority retained

`GET /api/v1/crews` remains authoritative for its existing contract, including:

- anonymous Crew discovery;
- keyword search;
- region filtering;
- pageable list behavior;
- newest-first ordering defined by the existing Crew repository/service path.

PF5 does not change, wrap, intercept, or reorder that endpoint.

## Cross-track boundaries

PF5 does not authorize:

- Recommendation P2 experiment changes;
- Search ranking or Search metric changes;
- reuse of Search exposure semantics;
- viewport or client-render impression tracking;
- changes to `recommendation_behavior_event` semantics;
- using `CREW_JOIN` as an exposure substitute;
- new training, release, or automated promotion gates derived from Crew exposure;
- backend replacement of `/api/v1/crews` ordering;
- removal of the legacy fallback path;
- SQL `63+` allocation.

Any of those requires a separate authority decision.

## Database and role boundary

No database migration is authorized or required by this decision.

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NONE
CANONICAL_SQL_TAIL=62
SQL_63_PLUS=UNALLOCATED
ROLE_GRANT_CHANGE=NONE
```

Existing APP, RECOMMENDATION, ADMIN and AUTH role boundaries remain unchanged.

## Implementation handoff

The intended first-party client decision function is logically equivalent to:

```text
if authenticated
   and default Crew discovery
   and keyword is absent
   and region is absent
   and initial batch <= 20:
       GET /api/v1/recommendation/crews?limit=<batch>
       preserve server order
else:
       GET /api/v1/crews using the existing legacy query/page contract
```

The implementation does not need a backend proxy or a second recommendation endpoint.

## Verification requirements

Client activation is complete only when the consuming client proves at minimum:

1. authenticated default Crew discovery selects `/api/v1/recommendation/crews`;
2. anonymous discovery selects `/api/v1/crews`;
3. keyword and region filtering select `/api/v1/crews` even for authenticated viewers;
4. recommendation response order is preserved without client-side sorting;
5. recommendation failure is not silently presented as recommendation success via legacy fallback;
6. personalized data is not shared across user identities through cache state;
7. existing legacy Crew discovery remains functional.

Backend exact-head verification remains required for any future backend runtime change, but PF5 itself allocates no backend runtime or SQL change.

## Production boundary

This decision grants **consumption authority** to a first-party client implementation. It does not by itself claim that any specific frontend deployment is live or that production traffic is currently using the recommendation endpoint.

A repository merge may establish implementation readiness. `DEPLOYED` or `ACTIVATED` may only be claimed from evidence that the consuming client version is actually running in the target environment.

## Completion state

PF5 governance allocation is complete when this decision is merged on top of prerequisite main `70e2b36df6b1c5b7d8623e8da4689fc0b024c364`.

The next implementation authority is then the consuming frontend repository. No SQL `63+` work is implied by PF5.
