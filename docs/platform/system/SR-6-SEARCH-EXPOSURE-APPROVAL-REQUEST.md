# SR-6 Search Exposure System Coordination Approval Record

## 상태

```text
Decision date: 2026-08-05 KST
Decision: APPROVED_BY_PROJECT_OWNER
Contract: search_exposure_v1
SR-6B Java contract: VERIFIED
SR-6C database/runtime implementation: VERIFIED
Merge/deploy: NOT_AUTHORIZED
Frontend impression activation: NOT_AUTHORIZED_IN_THIS_STAGE
```

## 승인값

| 항목 | 결정 |
|---|---|
| semantic owner | Intelligence Platform / Search |
| physical writer | Search runtime 단일 `SearchExposureStore` |
| application role | `jc_recommendation` compatibility arrangement |
| identity | `platform_subject_v1` |
| mapping owner | System Coordination / `jc_security_owner` |
| mapping access | purpose=`search-exposure-write`, requester=`intelligence-search` |
| anonymous exposure | V1 미지원 |
| visibility | viewport 50% 이상·연속 1,000ms 이상 |
| raw retention | 180일 |
| mapping audit retention | 30일 |
| deletion | mapping invalidation + controlled expiry purge |
| metric candidate | search CTR, 30분 attribution; SR-6F 별도 |
| protected baseline | `journey-connect-db-v2.7/01..54` 동결 |
| canonical target | `journey-connect-db-v2.8` |
| v2.8 sequence | 01 implementation / 02 digest grant / 03 smoke |

## Database version 결정

실제 저장소 보호 테스트는 `journey-connect-db-v2.7/01..54`를 RCA·IP reconciliation 기준선으로 고정한다. 따라서 이를 55·56으로 늘리지 않고 SR-6C를 `journey-connect-db-v2.8` 신규 package로 분리한다.

Backend Testcontainers는 기존 전체 bootstrap 순서를 유지하기 위해 동일 SQL bytes를 55·55a·56 label로 적용한다. Source package와 test bootstrap의 byte equality는 계약 테스트로 보호한다.

## 승인된 보안 경계

```text
JWT numeric user ID
→ jc_recommendation
→ SECURITY DEFINER resolve_platform_subject_v1
→ System Coordination mapping
→ opaque subject_ref
→ SearchExposureStore append-only insert
```

- Runtime은 mapping table 직접 권한이 없다.
- Mapping 접근마다 목적·requester audit를 남긴다.
- Invalidated mapping은 재발급·numeric fallback 없이 fail-closed한다.
- Exposure row에는 numeric user ID, raw query, JWT, precise location을 저장하지 않는다.
- Runtime writer는 Search exposure SELECT·INSERT만 보유하며 mutation과 purge 권한은 없다.
- 일반 recommendation·behavior impression·P2 exposure와 별도 authority를 유지한다.
- Flyway auto-discovery는 별도 baseline/history 승인 전까지 금지한다.

## 검증 승인 결과

```text
implementation head: fa6b618f473bd6285bc3a1002c93d2646e081fbe
SR workflow run: 30967288235
focused: 51 PASS
full backend: 325 PASS
```

## 계속 금지

- merge·배포
- frontend `IMPRESSION` 전송 및 운영 활성화
- 일반 recommendation/P2 denominator 합산
- raw query·JWT·numeric user ID 저장
- 별도 승인 없는 Flyway baseline/history 변경
