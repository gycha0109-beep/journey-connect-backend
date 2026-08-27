# SC PF6 Crew Open Chat Allocation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-pf6-crew-open-chat-allocation-v1` |
| status | `APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED` |
| prerequisite main | `673dac54aa2a1b6a118749ff5f1144015e2cae5f` |
| product slice | `JC-PF6 Crew Open Chat Access` |
| donor reference | `YTAK99/Journey-Connect PR #22` |
| canonical SQL tail before allocation | `62` |
| runtime owner | `APP` |

## Decision

PF6 allocates a narrow Crew open-chat access capability on top of the current personal-repository Crew authority.

The feature stores one optional external HTTPS URL on a Crew and exposes it only to the Crew owner or an APPROVED member. It does not implement chat, messaging, presence, WebSocket, SSE, push delivery, server-side URL fetching, or any provider-specific integration.

The authoritative product contract is:

```text
CONTRACT_ID=crew-open-chat-access-v1
ENTITY=crew
STORAGE_FIELD=crews.open_chat_url
MAX_LENGTH=500
VALUE_STATE=NULL_OR_HTTPS_URL
WRITE_AUTHORITY=CREW_OWNER_ONLY
READ_AUTHORITY=OWNER_OR_APPROVED_ONLY
ANONYMOUS_DISCLOSURE=NO
PENDING_DISCLOSURE=NO
REJECTED_DISCLOSURE=NO
CANCELLED_DISCLOSURE=NO
OUTSIDER_DISCLOSURE=NO
SERVER_SIDE_FETCH=NO
RECOMMENDATION_FEATURE=NO
DEPLOYMENT=NOT_CLAIMED
```

## URL contract

A non-empty value must:

- be an absolute URI;
- use the `https` scheme;
- contain a host;
- contain no user-info component;
- be at most 500 characters after request validation.

Blank input is normalized to `NULL`, allowing the owner to clear the link.

The backend validates URL shape only. It does not dereference the URL, test reachability, resolve redirects, inspect remote content, or infer that the external destination is safe or available.

## Access boundary

The API response may expose `openChatUrl` only when all of the following hold:

```text
open_chat_url IS NOT NULL
AND authenticated viewer exists
AND viewer relation IN {OWNER, APPROVED}
```

All other callers receive `openChatUrl = null` even though the database row may contain a value.

The authenticated viewer capability may include:

```text
canAccessOpenChat = true
```

only under the same condition. `PENDING`, `REJECTED`, `CANCELLED`, unrelated authenticated users and anonymous callers must never obtain the stored value through Crew list/detail/My Crews presentation.

## Write boundary

Existing Crew owner management authority remains authoritative.

Only the current Crew owner may set, replace, or clear `open_chat_url`, through the existing owner-controlled Crew update flow. PF6 does not add a second ownership model or a direct database write surface.

Membership transitions do not mutate the URL. Approval changes only whether a viewer is permitted to receive the already-stored URL.

## SQL allocation

### SQL 63

`63_crew_open_chat.sql`

Allocated responsibility:

- add nullable `open_chat_url VARCHAR(500)` to canonical `crews`;
- preserve all existing Crew columns, constraints and indexes;
- no new table;
- no recommendation/search/intelligence schema mutation;
- no role expansion beyond the existing APP ownership of Crew runtime.

### SQL 64

`64_crew_open_chat_smoke_test.sql`

Allocated responsibility:

- PostgreSQL 15/18 column/type/nullability verification;
- verification that the existing Crew table remains available to the APP runtime;
- verification that no unrelated recommendation/search exposure authority is changed.

SQL `65+` remains unallocated by this decision.

## Recommendation and exposure boundary

PF6 must not alter Crew recommendation candidate retrieval, eligibility, score, ordering, reason codes, exposure identity, or exposure persistence.

`open_chat_url` is presentation-only gated data and is not a ranking feature. In particular:

- `crew-recommendation-contract-v1` remains unchanged;
- `crew-ranking-policy-v1` remains unchanged;
- `crew_recommendation_exposure_v1` remains unchanged;
- `CREW_JOIN` feedback semantics remain unchanged;
- public legacy `/api/v1/crews` ordering remains unchanged;
- no client re-ranking or recommendation fallback semantics are introduced.

Recommended Crew candidates already exclude owner/pending/approved relations under the current recommendation contract, so personalized recommendation presentation must not use open-chat availability as a score or eligibility signal.

## Security boundary

PF6 explicitly forbids:

- exposing a stored URL to PENDING applicants;
- exposing a stored URL to anonymous or unrelated users;
- accepting `http`, scheme-relative, hostless, or user-info URLs;
- server-side HTTP requests to the configured URL;
- treating the URL as trusted content;
- creating a generic redirect/proxy endpoint;
- widening AUTH, RECOMMENDATION, SEARCH or unrelated role write authority.

## Donor provenance

`YTAK99/Journey-Connect` PR #22 is a donor/reference for the product idea and its basic access semantics only.

The personal repository must adapt the feature to its canonical `crews` table, APP transaction boundary, current viewer model, canonical SQL sequence, role model and protected recommendation authorities. Team Flyway numbering and source layout are not authoritative here.

## Completion gate

Implementation is complete only after a successor implementation PR from the merged PF6 allocation main proves at minimum:

- owner may set/change/clear a valid HTTPS URL;
- invalid URL forms fail with a stable domain error;
- owner receives the URL;
- APPROVED member receives the URL;
- PENDING/REJECTED/CANCELLED/outsider/anonymous callers do not receive the URL;
- list/detail/My Crews presentation preserves the disclosure rule;
- canonical SQL 63/64 copies are byte-identical where required;
- PostgreSQL 15/18 canonical integration passes;
- Backend protected regression passes;
- Recommendation/P0/Admin/PIE successor gates remain green.

Until exact-head verification passes, implementation state is `IMPLEMENTED_UNVERIFIED` and no merge is authorized.

## Non-goals

This allocation does not authorize:

- direct messages or group messaging;
- WebSocket/SSE/push chat transport;
- Kakao/Discord/Telegram APIs;
- URL crawling or preview generation;
- external-service account linking;
- deployment or production activation;
- any SQL after 64.
