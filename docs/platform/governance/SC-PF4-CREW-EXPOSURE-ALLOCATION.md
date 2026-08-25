# SC PF4 Crew Recommendation Exposure Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf4-crew-exposure-allocation-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `744d0b29bf25e821d3c36e1e768b541512c20b4a` |
| prerequisite product slice | `JC-PF4 Crew Intelligence read path / PR #81` |
| governing contract | `crew-recommendation-contract-v1` |
| exposure registry ID | `crew_recommendation_exposure_v1` |
| registry owner | `SYSTEM_COORDINATION` |
| canonical SQL tail before allocation | `60` |

## Decision

System Coordination registers a Crew-specific recommendation exposure authority for the authenticated Crew recommendation read path.

The registered exposure contract is:

```text
CONTRACT_ID=crew_recommendation_exposure_v1
SURFACE=crew_list
ENDPOINT=/api/v1/recommendation/crews
ENTITY_TYPE=crew
EXPOSURE_SEMANTIC=server_delivery_commit_v1
PERSISTENCE_REQUIRED_BEFORE_RESPONSE=YES
PERSISTENCE_FAILURE_BEHAVIOR=FAIL_PERSONALIZED_RESPONSE
VIEWPORT_IMPRESSION_SEMANTIC=NO
P2_EXPERIMENT_EXPOSURE_AUTHORITY=UNCHANGED
SEARCH_EXPOSURE_AUTHORITY=UNCHANGED
LEGACY_CREW_LIST_AUTHORITY=UNCHANGED
```

This decision resolves the CR-0 `PROPOSED_NOT_REGISTERED` blocker only for Crew recommendation delivery evidence. It does not transfer authority from Recommendation P2, Search, the legacy Crew list, or behavior-event storage.

## Exposure semantic

`server_delivery_commit_v1` means:

1. the authenticated request has produced a deterministic ranked Crew result;
2. APP-role presentation data required by the response has been resolved successfully;
3. the exact response candidate set and ranking metadata have been prepared;
4. Crew exposure evidence is durably committed under the Recommendation role;
5. only after that commit succeeds may the personalized response be emitted by the endpoint.

It does **not** mean that the client rendered the response, that an item entered the viewport, or that the user consciously saw an item. Network failure after the server commit remains possible and must not be re-labelled as a viewport impression.

A future client-render/viewport contract, if required, needs a separate registry decision.

## Required exposure identity

The implementation must persist one immutable exposure event and its exact candidate set.

Event identity must include at minimum:

- stable `exposure_id`;
- `user_id`;
- `surface = crew_list`;
- `served_at`;
- ranking `reference_time`;
- `contract_version`;
- `ranking_policy_version`;
- `score_policy_version`;
- requested limit and returned count;
- canonical response/ranking fingerprint.

Candidate evidence must include at minimum:

- `exposure_id`;
- absolute rank;
- `crew_id`;
- deterministic score;
- coverage mode;
- canonical candidate evidence sufficient to detect duplicate/conflicting writes.

The candidate set is the denominator for this Crew delivery contract only. It is not a Recommendation P2 experiment denominator and not a Search CTR denominator.

## SQL allocation

### SQL 61

`61_crew_recommendation_exposure.sql`

Allocated responsibility:

- Crew-specific immutable exposure event storage;
- exact exposure candidate storage;
- event/candidate binding constraints;
- append-only protection;
- deterministic duplicate/conflict identity;
- least-privilege Recommendation write path;
- Administrator read-only inspection;
- no APP/AUTH direct mutation authority.

### SQL 62

`62_crew_recommendation_exposure_smoke_test.sql`

Allocated responsibility:

- PostgreSQL 15/18 schema and constraint verification;
- append-only verification;
- role/grant verification;
- event/candidate binding verification;
- duplicate/conflict verification;
- proof that P2 and Search exposure authorities remain unchanged.

SQL `63+` remains unallocated by this decision.

## Role boundary

Authorized runtime direction:

```text
RECOMMENDATION read transaction
  deterministic Crew ranking
        ↓
APP read transaction
  CrewDtos.View presentation resolution
        ↓
RECOMMENDATION write transaction
  crew_recommendation_exposure_v1 commit
        ↓
HTTP personalized response emission
```

A single database transaction may not switch APP and RECOMMENDATION roles. Existing `DatabaseRoleBoundary` enforcement remains authoritative.

`jc_recommendation` may receive only the exposure storage permissions required by SQL 61. `jc_admin` may inspect the evidence read-only. `jc_app`, `jc_auth` and unrelated platform roles must not receive direct write access.

## Explicit non-reuse boundaries

The following are forbidden:

- writing Crew delivery evidence into `recommendation_p2_experiment_exposure`;
- interpreting Crew delivery as a P2 baseline/treatment assignment or P2 experiment exposure;
- reusing Search exposure tables or Search CTR denominator semantics;
- reinterpreting `recommendation_behavior_event` IMPRESSION as Crew delivery evidence;
- treating `CREW_JOIN` behavior feedback as exposure evidence;
- counting the public legacy `/api/v1/crews` feed as Crew recommendation exposure;
- claiming viewport impression from server delivery evidence.

## Activation boundary

This allocation authorizes implementation and verification of Crew exposure persistence for the already-separated authenticated endpoint `/api/v1/recommendation/crews`.

It does **not** authorize:

- replacing the ordering of public `/api/v1/crews`;
- removing the legacy newest-first fallback;
- automatic production cutover of the legacy Crew list;
- Recommendation P2 metric or experiment changes;
- Search metric changes;
- client-side viewport tracking;
- using Crew exposure data as a new training or release gate without a separate contract.

## Completion gate

Crew exposure implementation may be considered complete only when the exact implementation head passes all applicable current-main gates, including:

- Backend protected readiness;
- canonical PostgreSQL 15 and 18 integration;
- ADM-1 and ADM-3 successor compatibility;
- PIE prospective shadow;
- explicit Crew exposure integration tests proving that a successful personalized API result is backed by one immutable exposure event with the exact returned candidate set.

Until those gates pass, implementation state is `IMPLEMENTED_UNVERIFIED` and no merge is authorized.
