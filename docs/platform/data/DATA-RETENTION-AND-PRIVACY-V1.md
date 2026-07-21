# Data Retention and Privacy V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `data-retention-privacy-v1` |
| 상태 | `RECOVERED / TECHNICAL POLICY DRAFT` |
| 소유 | Data Platform |
| legal status | internal technical baseline, not legal advice |

## Principles

- collect only purpose-bound minimum fields
- no unrestricted user text, raw search/report content, token/secret/password/provider credential
- canonical actor defaults to `subject:<opaque-id>`
- identity mapping is separated from raw events
- user PK/raw identity is not copied into canonical payload/logs
- exact location requires separate approved taxonomy; prefer region/place refs

## Identity

```text
subject:<opaque-id> != user:<numeric-id>
```

Mapping requires separate owner, purpose binding, allowlisted reads, audit, version, invalidation and deletion policy. DP-1 implements no mapping repository/join. Mapping failure does not fallback to anonymous or another subject.

## Payload policy

Allowed examples: bounded surface/position/rank/duration, versioned policy/ref IDs, coarse reason codes, queryRef/hash.

Prohibited examples: post/comment/report text, raw query, email/phone/address, JWT/token, share recipient, private profile fields, exact GPS, raw provider response, numeric account ID copied from protected source.

## Retention

Retention periods in earlier DP-0 material are technical proposals only and require legal/Operations/Security approval. Every persisted class must have a versioned retention policy. Protected P0/P1/P2 retention remains with current owners and is not changed by DP-0.

## Deletion/erasure

Approved procedure may invalidate/delete/crypto-erase restricted mapping, block future ingest, rebuild active projections, supersede snapshots and record minimal deletion evidence. It must not edit historical records to pretend processing never occurred. Legal physical deletion exceptions require separate approval and leave only non-content operation evidence where permitted.

## Aggregate retention

Longer retention is considered only when direct identity is absent, small/rare cohorts are suppressed, minimum cohort threshold applies, purpose/metric is explicit and reconstruction is infeasible. Otherwise aggregate follows subject deletion.

## Audit/logging

Audit stores operation/reference IDs, privacy-safe actors, reason/status hashes, timestamps/build IDs—not raw payload/text. Logs prohibit raw mapping, token/sessionToken, full idempotency key, canonical payload, raw metadata/query/report and exact location.

## Test fixtures

Use synthetic subjects/entities and obvious test namespaces. No production dumps/tokens. P0 fixtures preserve canonical bytes/hash relation after PII removal and record fixture generation version.

## Open policy items

Country-specific retention/deletion, minors, location regulation, profiling notice, audit legal hold, cross-border providers and aggregate anonymity require separate legal/privacy decisions.
