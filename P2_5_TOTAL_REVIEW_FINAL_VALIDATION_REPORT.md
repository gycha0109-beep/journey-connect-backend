# P2-5 Total Review & Final Validation Report

## 판정

**P2 기술 구현: CLOSED**

**운영 출시 상태: HOLD**

기술 계약과 실행 경로는 완료되었으나 실제 운영 표본이 없으므로 CANARY/LIVE 성과 승격은 선언하지 않는다.

## 총 리뷰에서 발견·보완한 사항

1. P1 treatment만 기록되고 baseline 배정·노출 증거가 없던 문제
   - 결정론적 baseline/treatment assignment와 실제 run exposure 저장 추가
2. treatment run을 정책 문자열 prefix로 추측하던 문제
   - P1 policy assignment의 `treatment_run_id`로 결속
3. metric version이 수치 threshold만 고정하고 분자·분모 의미를 고정하지 않던 문제
   - numerator/denominator/eligibility/deduplication을 core contract에 추가
4. 작은 표본의 극단값으로 즉시 rollback할 수 있던 문제
   - 표본·데이터 품질·CI 조건을 갖춘 severe regression만 rollback
5. release decision DB trigger가 gate evidence를 확인하지 않던 문제
   - Gate A~E 완전성과 상태를 DB에서 재검증
6. 동일 상태 HOLD에도 release transition을 생성하던 문제
   - 실제 상태 변경 시에만 decision 생성
7. stale unexposed assignment가 장기 분모를 오염할 가능성
   - 관측 기간 내 assignment 또는 실제 exposure만 observation에 포함
8. P2 finite-check 함수 role execute 권한 누락
   - 명시적 function grant 추가
9. 구현 초안 Java SQL text-block 문법 오류
   - backend P2 persistence 클래스를 정상 Java 형식으로 재작성하고 컴파일 게이트 추가
10. transaction exception translation과 테스트 기대 타입 불일치
    - Spring DataAccessException의 root cause로 binding conflict를 검증하도록 보완

## 최종 검증

- Java Core 전체 회귀 PASS
  - Wave 1~7
  - committed golden fixtures
  - framework isolation
  - P1 core 17 scenarios
  - P2 core 23 scenarios
- Backend 전체 test **83/83 PASS**
- Backend 계약 PASS
  - P0 verification
  - P1 contract verification
  - P2 contract verification **5/5 PASS**
- PostgreSQL 통합 PASS
  - P1 preference/profile/shadow/canary **4/4 PASS**
  - P2 assignment/exposure/evaluation/release **1/1 PASS**
- canonical SQL `01..26` PASS
- P1 DB v2.6 exact unchanged
- pre-P2 Java core source/test exact unchanged
- P0/P1 단계 문서 exact unchanged

## 호환성

- P2 assignment runtime은 기본 비활성이다.
- 비활성 시 기존 P1 CANARY allocation 동작은 유지된다.
- 기존 P0/P1 policy, replay, snapshot, SQL `01..24`를 수정하지 않았다.
- 신규 P2 schema는 DB v2.7의 forward SQL `25..26`으로 추가했다.

## 잔여 리스크

- 실제 사용자 표본과 성과 데이터 없음: release decision은 HOLD
- Gradle 9 비호환 deprecation warning 잔존
- 운영 활성화 전 experiment config와 관리자 승인 경로 확정 필요
