# P1-4 SHADOW, CANARY & Rollback

## 목적

P0 baseline을 그대로 보존하면서 P1 treatment를 별도 run으로 실행·저장·비교하고, SHADOW 관측과 제한 CANARY 제공 및 즉시 baseline rollback을 보장한다.

## 주요 변경 파일

- `jc-backend/.../RecommendationP1RuntimeService.java`
- `jc-backend/.../RecommendationP1EvidenceStore.java`
- `jc-backend/.../RecommendationShadowService.java`
- `jc-backend/.../RecommendationCanaryService.java`
- P1 SHADOW/CANARY PostgreSQL integration tests

## 구현

- 기존 P0 run을 immutable baseline으로 조회
- P1 profile·policy·ranking·comparison을 별도 treatment run에 저장
- snapshot/run/candidate/profile/assignment/comparison 증거 결속
- 후속 페이지는 저장된 treatment run 순위를 재생
- SHADOW는 baseline 응답 유지, treatment evidence만 저장
- CANARY는 cohort 대상만 treatment run 선택
- P1 처리 실패 시 기존 P0 baseline exact replay
- treatment 생성 전체를 recommendation-role 트랜잭션으로 묶어 부분 증거 방지
- 저장 직후 fingerprint·rank·score raw bits 재검증

## 검증 및 보완

- 실제 baseline/treatment 분리와 비교 evidence PASS
- CANARY 성공·OFF baseline 유지·강제 실패 rollback PASS
- 실패 treatment의 run/profile/assignment/comparison 잔존 없음 PASS
- 같은 baseline 입력의 treatment fingerprint 재현 PASS
- 비교 ID를 content-derived 값에서 append-only 실행 ID로 분리해 재실행 충돌 보완
- storage ID와 알고리즘 fingerprint 경계 보완

## 잔여 리스크

- LIVE 전환은 P1 범위가 아니다. P2의 표본·효과·guardrail·운영 승인 게이트 통과 전 전면 승격을 금지한다.
