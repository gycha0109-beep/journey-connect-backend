# IP-0 Track Change Proposal

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 제안 ID | `ip-0-track-change-proposal-v1` |
| 상태 | `PROPOSED / SYSTEM_COORDINATION_APPROVAL_REQUIRED` |
| 제안 트랙 | Intelligence Platform |
| 변경 유형 | 공통 기준선·ownership registry 정합화 |
| production 영향 | 없음 |
| DB 영향 | 없음 |

## 2. 제안 이유

현재 `JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md`와 `JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md`는 작성 당시 추천 P0와 DB v2.5/01..22를 기준으로 한다. 실제 보호 기준선은 이후 P1/P2까지 진행되었다.

실제 현재 상태:

- P1 `CLOSED`
- P2 technical `CLOSED`
- P2 production `HOLD`
- canonical DB `v2.7/01..26`
- Backend `83/83 PASS`
- P1 Core `17/17 PASS`
- P2 Core `23/23 PASS`

DP-0은 별도 P2 Baseline Alignment amendment로 이 차이를 보정했으나 공통 System Contract 본문과 Governance의 일부 예시/통합 순서는 여전히 P0 시점이다. IP-0이 이를 임의 수정하면 총괄 계약 변경 절차를 위반하므로 승인 제안으로 분리한다.

## 3. 제안 변경

### 3.1 System Contract 기준선 metadata 정합화

변경 후보:

| 항목 | 현재 표기 | 제안 표기 |
|---|---|---|
| 기준 추천 상태 | P0 CLOSED | P1 CLOSED, P2 TECHNICAL CLOSED, PRODUCTION HOLD |
| 기준 DB | v2.5/01..22 | v2.7/01..26 |
| 보호 계약 | P0 중심 | P0/P1/P2 보호 + P2 amendment 참조 |

기존 P0 호환성 예외와 계약 본문을 삭제하지 않는다.

### 3.2 P2 ownership matrix 명시

공통 계약에 다음을 명시한다.

- current physical owner/write path: recommendation P2 package, recommendation DB role
- semantic owner: Reliability
- current status: protected compatibility arrangement
- migration: 별도 High-risk proposal + 전체 회귀 필요

### 3.3 identity scheme registry 추가

- `platform_subject_v1 = subject:<opaque-id>`
- `legacy_user_numeric_v1 = user:<numeric-id>`
- restricted mapping 없이는 상호 교환 금지

### 3.4 exposure source registry 추가

- `recommendation_general_exposure_v1`
- `recommendation_behavior_impression_v1`
- `recommendation_p2_experiment_exposure_v1`
- future `search_exposure_v1`

동일 의미가 아니며 metric별 authority를 요구한다.

### 3.5 Intelligence common contract IDs 예약

- `intelligence-run-v1`
- `intelligence-input-snapshot-v1`
- `intelligence-output-snapshot-v1`
- `intelligence-candidate-snapshot-v1`
- `intelligence-feature-value-v1`
- `intelligence-explanation-v1`
- `model-inference-record-v1`

예약은 runtime 구현 또는 DB table 생성을 의미하지 않는다.

## 4. 영향 분석

### Data

- DP-0 P2 amendment와 정합화된다.
- shadow-only 원칙은 유지된다.
- 신규 Data write나 DB 변경은 없다.

### Intelligence

- 기존 P1/P2를 reference implementation으로 보호한다.
- 공통 계약 adapter 구현의 기준이 생긴다.

### Operations

- P2 HOLD가 실패가 아니라 운영 증거 대기 상태임을 명확히 한다.
- 향후 관측/승인 port 설계 기준이 생긴다.

### Reliability

- semantic ownership을 명시하되 current physical path를 즉시 이전하지 않는다.
- metric/exposure authority가 강화된다.

### System Coordination

- contract registry, identity/exposure vocabulary, DB baseline을 최신화해야 한다.

## 5. 호환성

- production Java/Kotlin/SQL 변경 없음
- 기존 contract ID 삭제 없음
- 기존 P0/P1/P2 row/hash 변경 없음
- 기존 wire enum 변경 없음
- DP-0 계약 의미 변경 없음
- 문서 metadata와 registry 보완만 제안

## 6. 승인 선택지

### A. 승인

System Contract V1 amendment 또는 V1.1을 발행하고 Governance에 P2 current-state 부록을 추가한다.

### B. 부분 승인

기준선 metadata와 identity/exposure registry만 우선 반영하고 physical ownership 이전은 미결정으로 둔다.

### C. 보류

IP-0 문서는 local track contract로 유지하되, 공통 registry에 등록하기 전 IP-1 cross-track implementation을 HOLD한다.

## 7. 권장 판정

`부분 승인`을 권장한다.

- 즉시 필요한 것: P2 기준선, identity scheme, exposure authority, common contract ID 예약
- 추후 결정할 것: P2 physical ownership migration 시점과 구현 형태

## 8. 승인 전 금지

- System Contract 원문을 IP 트랙에서 직접 수정
- P2 table role/grant 변경
- identity bulk migration
- Data shadow source를 runtime으로 전환
- P2 experiment exposure를 일반 exposure로 통합
