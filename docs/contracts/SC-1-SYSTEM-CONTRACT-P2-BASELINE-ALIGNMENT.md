# SC-1 System Contract P2 Baseline Alignment

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `SC-1` |
| 단계명 | `System Contract P2 Baseline Alignment` |
| 상태 | `COMPLETE / PARTIAL APPROVAL` |
| 소유 | System Coordination |
| 기준일 | `2026-07-19` |
| production 코드 영향 | 없음 |
| SQL/DB 영향 | 없음 |

## 2. 목적

기존 System Contract와 Track Governance에 남아 있던 추천 P0·DB v2.5 기준 표현을 현재 검증된 P1/P2·DB v2.7 기준선으로 정합화하고, IP-0에서 요청한 공통 contract·identity·exposure registry를 승인한다.

## 3. 확정 기준선

- 추천 P0: `CLOSED`
- 추천 P1: `CLOSED`
- 추천 P2 기술 구현: `CLOSED`
- 추천 P2 운영 출시: `HOLD`
- Backend: `83/83 PASS`
- P1 Core: `17/17 PASS`
- P2 Core: `23/23 PASS`
- canonical DB: `journey-connect-db-v2.7/01..26`

`HOLD`는 실제 CANARY 증거와 운영 승인 대기 상태다.

## 4. 승인 결과

### 4.1 승인

- System Contract V1의 P2 기준선 개정
- Track Governance V1의 현재 통합 순서 개정
- `intelligence-run-v1`
- `intelligence-input-snapshot-v1`
- `intelligence-candidate-snapshot-v1`
- `intelligence-output-snapshot-v1`
- `intelligence-feature-value-v1`
- `intelligence-explanation-v1`
- `model-inference-record-v1`
- `platform_subject_v1`
- `legacy_user_numeric_v1`
- `recommendation_general_exposure_v1`
- `recommendation_behavior_impression_v1`
- `recommendation_p2_experiment_exposure_v1`
- `search_exposure_v1` ID 예약
- IP-1 module: `jc-intelligence-contracts`
- IP-1 package: `com.jc.intelligence.contract`

### 4.2 보호 조건

- Data P1/P2 projection은 shadow-only
- P2 experiment exposure authority는 `recommendation_p2_experiment_exposure`
- `engagement_rate`와 `fallback_rate` 의미 불변
- P2 physical writer는 현재 recommendation P2 package/DB role 유지
- Reliability는 P2 experiment/evaluation/release의 semantic owner
- 기존 P2 rows, canonical bytes, content hash, release evidence rewrite 금지

### 4.3 보류

- P2 physical ownership migration
- restricted identity mapping의 물리 owner와 삭제 정책
- search exposure의 실제 persistence model
- DP-2 신규 DB version/SQL sequence

## 5. 변경 파일

1. `JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md`
2. `JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md`
3. `IP-0-INTELLIGENCE-PLATFORM-CONTRACT-FOUNDATION.md`
4. `IP-0-P2-COMPATIBILITY-AND-DP0-INTEGRATION.md`
5. `IP-0-HANDOFF.md`
6. `SC-1-SYSTEM-CONTRACT-P2-BASELINE-ALIGNMENT.md`

## 6. IP-1 판정

`READY`

허용 범위:

- Java common contract type
- validator
- JSON fixture
- recommendation read-only compatibility adapter
- contract test
- 기존 추천 전체 회귀

금지 범위:

- DB/SQL 변경
- source cutover
- P2 ownership migration
- identity 실제 join
- search/content/planner production 구현

## 7. 자체 리뷰 결과

- stale P0-only baseline 제거: PASS
- canonical DB v2.7/01..26 정합성: PASS
- P2 technical CLOSED / production HOLD 분리: PASS
- contract ID 중복: PASS
- identity scheme 충돌 방지: PASS
- exposure authority 분리: PASS
- P2 metric 의미 보호: PASS
- physical writer / semantic owner 구분: PASS
- production 코드·SQL 변경 없음: PASS
- IP-1 범위 과확장 방지: PASS
