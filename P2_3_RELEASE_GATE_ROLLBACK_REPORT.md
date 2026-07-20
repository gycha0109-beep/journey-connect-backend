# P2-3 Release Gate & Rollback Report

## 목적

계약 무결성, 데이터 품질, 표본 충분성, 성과·guardrail, 운영 승인을 독립 게이트로 유지하고 승인된 상태 전이만 append-only로 기록한다.

## 구현 내용

### Gate

- Gate A: contract/calculation integrity
- Gate B: data quality
- Gate C: sample sufficiency
- Gate D: primary performance and guardrail
- Gate E: operational approval

### 상태 전이

```text
DRAFT → SHADOW → CANARY → LIVE
   ↘ HOLD      ↘ HOLD/ROLLED_BACK
LIVE → HOLD/ROLLED_BACK
ROLLED_BACK → SHADOW
```

### 판정

- 모든 게이트 PASS + 유효한 전이: CANARY 또는 LIVE
- 게이트 미충족: HOLD
- CANARY/LIVE에서 충분한 통계 증거가 있는 severe guardrail regression: ROLLBACK

## DB 무결성

- evaluation, metric result, gate result, release decision은 append-only
- release decision은 실제 상태가 변경될 때만 생성
- 동일 상태 HOLD는 evaluation evidence만 추가하고 가짜 transition row를 만들지 않음
- release trigger가 evaluation binding과 상태 전이를 검증
- CANARY/LIVE는 gate 5개 전부 PASS일 때만 저장 가능
- ROLLBACK은 Gate D가 FAIL일 때만 저장 가능
- gate evidence가 없는 forged release decision은 SQLSTATE `23514`로 거부

## 검증 결과

- 상태기계 계약 PASS
- operational approval gate PASS/HOLD 검증 PASS
- invalid transition HOLD 검증 PASS
- append-only update 거부 PASS
- forged release without gates 거부 PASS
- PostgreSQL role/grant 검증 PASS

## 보완 사항

- 최초 release trigger는 application이 작성한 final decision만 신뢰했다.
- DB trigger가 gate row 개수와 상태를 독립 검증하도록 보완하여 직접 INSERT를 통한 허위 승격을 차단했다.
- 반복 HOLD 평가에서 불필요한 release decision을 생성하지 않도록 보완했다.

## 잔여 리스크

- 운영 actor authorization은 관리자 API/Operations 트랙과 연결되어야 한다.
- 본 배치는 저장·판정 계약을 구현했으며, 실제 LIVE 승인은 운영 표본과 별도 승인 없이는 수행하지 않는다.
