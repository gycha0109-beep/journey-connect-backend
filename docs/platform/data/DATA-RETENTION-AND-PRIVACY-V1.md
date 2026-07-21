# Data Retention and Privacy V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `data-retention-privacy-v1` |
| 상태 | `ACTIVE TECHNICAL BASELINE / LEGAL APPROVAL PENDING` |
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

Mapping requires separate owner, purpose binding, allowlisted reads, audit, version, invalidation and deletion policy. DP-2 implements no mapping repository/join. Mapping failure does not fallback to anonymous or another subject.

## Payload policy

Allowed examples: bounded surface/position/rank/duration, versioned policy/ref IDs, coarse reason codes, queryRef/hash.

Prohibited examples: post/comment/report text, raw query, email/phone/address, JWT/token, share recipient, private profile fields, exact GPS, raw provider response, numeric account ID copied from protected source.

## Retention technical baseline

| Persisted class | Technical baseline | Enforcement state |
|---|---:|---|
| idempotency binding | 30 days from first accepted binding | DP-2 may enforce lookup expiry; deletion job disabled |
| ingest attempt/conflict/quarantine evidence | 90 days | metadata/expiry may be stored; automatic purge disabled |
| canonical platform event | 365-day default retention class | metadata/expiry may be stored; automatic purge disabled |

Every persisted class records a versioned retention policy or class. Protected P0/P1/P2 retention remains with current owners and is not changed.

These periods are SC-approved engineering defaults for schema, capacity and expiry semantics. They are not final legal retention periods. Operations/Security/Privacy may shorten, extend under legal hold, or approve erasure through a versioned policy decision.

## Deletion/erasure

- DP-2 may store `retention_policy_version`, `retention_class` and `expires_at`.
- DP-2 must not implement an enabled production purge, physical deletion or crypto-erasure executor.
- approved future procedure may invalidate/delete/crypto-erase restricted mapping, block future ingest, rebuild active projections, supersede snapshots and record minimal deletion evidence.
- historical records must not be edited to pretend processing never occurred.
- legal physical deletion exceptions require separate approval and leave only non-content operation evidence where permitted.

## Aggregate retention

Longer retention is considered only when direct identity is absent, small/rare cohorts are suppressed, minimum cohort threshold applies, purpose/metric is explicit and reconstruction is infeasible. Otherwise aggregate follows subject deletion.

## Audit/logging

Audit stores operation/reference IDs, privacy-safe actors, reason/status hashes, timestamps/build IDs—not raw payload/text. Logs prohibit raw mapping, token/sessionToken, full idempotency key, canonical payload, raw metadata/query/report and exact location.

## Test fixtures

Use synthetic subjects/entities and obvious test namespaces. No production dumps/tokens. P0 fixtures preserve canonical bytes/hash relation after PII removal and record fixture generation version.

## Open policy items

Country-specific retention/deletion, minors, location regulation, profiling notice, audit legal hold, cross-border providers and aggregate anonymity require separate legal/privacy decisions.

SC entry decision: `../governance/SC-DP2-ENTRY-DECISIONS.md`.
