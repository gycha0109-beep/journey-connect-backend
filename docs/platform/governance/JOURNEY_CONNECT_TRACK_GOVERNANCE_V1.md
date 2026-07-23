# Journey Connect Track Governance V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 개정 | `V1.2 / SC DP-1 BASELINE RECONCILIATION` |
| 상태 | `ACTIVE` |
| canonical DB | `journey-connect-db-v2.7/01..28` |
| SC 기준 | [System Contract](JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md) |

## 2. 책임

### Data

Own: canonical platform event, validation, idempotency, retry/quarantine/replay, versioned datasets, quality, lineage, privacy technology policy.

Not own: recommendation/search calculation, moderation decision, experiment metric/release, 다른 트랙 table write.

### Intelligence

Own: Recommendation/Search/Content/Trip runtime meaning, policy/model/prompt, run/snapshot/provenance. Existing P0/P1/P2 recommendation path is protected.

### Operations

Own: admin authorization, moderation/visibility/eligibility, operator audit, stop/hold/override controls.

### Reliability

Own: experiment definition/assignment semantics, metric/denominator/attribution, evaluation, release/rollback evidence. Current P2 physical path remains protected compatibility arrangement.

### System Coordination

Own: System Contract, registries, DB sequence, integration order, breaking-change decisions, final conflict/go-no-go classification.

## 3. Authoritative execution sequence

```text
IP 기술 기준선 종결
→ DP
→ OP
→ RP
→ 교차 트랙 통합 검증
```

과거 `DP-1/IP-1 병렬 진행`은 DB 비변경 작업이 기술적으로 병렬 가능하다는 **historical recommendation**이다. 현재 authoritative sequence보다 우선하지 않으며 DP-1 시작 기준을 변경하지 않는다.

## 4. DP-1 boundary

Allowed:

- reserved `jc-data-contracts` / `com.jc.data.contract`
- Java contract type/validator/canonicalization fixture/contract test
- DB와 runtime 비변경

Not allowed in this reconciliation:

- module/source 생성
- event ingestion/runtime/persistence
- SQL/new migration
- identity mapping
- projection cutover

## 5. DB governance

- baseline `01..28`
- SQL 27 Search derived projection + Operations eligibility
- SQL 28 smoke test
- DP-2 이후 SQL은 SC가 28 이후 배정
- 한 PR에서 여러 트랙의 write contract를 혼합하지 않는다.
- 기존 SQL을 수정하지 않고 forward migration을 원칙으로 한다.

## 6. Branch/PR separation

- PR #3: `codex/ip-12-5-readiness`, IP technical controls only
- SC reconciliation: `codex/sc-dp1-baseline-reconciliation`, docs/registry/evidence only
- PR #3 미병합 상태를 main authoritative state로 기록하지 않는다.
- 사용자 명시 지시 없이 PR #3을 병합하지 않는다.

## 7. Change proposal gate

구현 전 SC proposal 필요:

- 공통 ID/enum/time/version/canonicalization/fingerprint 변경
- 다른 트랙 read/write 추가
- event family/type 추가
- snapshot/hash 또는 identity/privacy 변화
- DB sequence/role/grant 변화
- runtime source/consumer cutover

## 8. Integration refusal

- duplicate registry value
- unversioned schema/policy/metric
- direct cross-track write
- SQL 미검증/sequence collision
- P0/P1/P2 replay/golden regression
- exposure authority/metric confusion
- unauthorized identity mapping/cutover
- docs/implementation mismatch
- PR #3 operational HOLD를 traffic approval로 오해

## 9. Canonical governance paths

- Decision Register: [SC-DECISION-REGISTER.md](SC-DECISION-REGISTER.md)
- RACI: [SC-RACI.md](SC-RACI.md)
- Registry: [SC-PLATFORM-REGISTRY.md](SC-PLATFORM-REGISTRY.md)
- Handoff: [SC-HANDOFF.md](SC-HANDOFF.md)
