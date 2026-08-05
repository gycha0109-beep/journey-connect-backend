# SR-6 Search Exposure System Coordination Approval Record

## 상태

```text
Decision date: 2026-08-05 KST
Decision: APPROVED_BY_PROJECT_OWNER
Contract: search_exposure_v1
SR-6B Java contract: VERIFIED
SR-6C database/runtime implementation: AUTHORIZED
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
| canonical target | `journey-connect-db-v2.7` forward extension |
| SQL sequence | 55 implementation / 56 smoke |

## 번호 재조정

초기 문서의 `01..26`과 달리 실제 canonical bootstrap에는 27·28 Search projection 및 53·54 Admin hardening이 이미 포함되어 있다. 따라서 충돌 없이 다음 번호 55·56을 배정한다.

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
- 일반 추천·P2 exposure와 별도 authority를 유지한다.

## 계속 금지

- merge·배포
- frontend `IMPRESSION` 전송 및 운영 활성화
- 일반 추천/P2 denominator 합산
- raw query·JWT·numeric user ID 저장
