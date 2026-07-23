# Data Platform Change Policy V1

## Contracts

Breaking field/enum requires a new schema/contract version. Semantic change requires a new contract or policy version. Optional additive fields require compatibility review. Authority change requires SC decision. Identity mapping requires SC + privacy/security review. Retention/deletion change requires privacy/SC approval.

## SQL

SQL `01..52` is immutable. New objects require SC-allocated SQL `53+`. Existing behavior changes use forward migration; historical migration rewrite is prohibited. Every migration includes owner, roles, validation, rollback/forward-fix and PostgreSQL 15/18 evidence.

## Fingerprints

Semantic or canonical-byte change requires a new domain/version. Old fingerprint reinterpretation and historical hash rewrite are prohibited.

## Policies

Quality threshold or zero-denominator semantics require a new quality policy. Integration verdict semantics require a new integration policy. Retry/retention/privacy semantics require new policy and owner review. Historical evidence remains append-only.

## Consumers

New consumers require contract/authority/privacy/lineage/quality/Reliability review. Shadow consumers require explicit authorization and cannot replace authority. Production consumers require staged adoption, observability and rollback. Direct table write/shared ORM ownership is prohibited.
