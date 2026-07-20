# P1-5 Final Validation & Closure

## 환경

- Java 21.0.10
- 사용자 제공 Gradle 8.14.5
- 사용자 제공 PostgreSQL 15 Bookworm rootfs / PostgreSQL 15.18
- 기준 계약: Java core 순수성, 정책 병렬 버전, append-only evidence, P0 replay 불변

## 최종 결과

- Backend JUnit: **39개 클래스 / 67개 테스트 PASS**
- P1 전용 Backend gate: **13/13 PASS**
  - static/mode/mapper: 9
  - preference/behavior/SHADOW/CANARY PostgreSQL integration: 4
- Java Core 기존 Foundation~Wave 7 + golden + isolation: **11개 계약 task PASS**
- P1 Java Core: **17개 계약 시나리오 PASS**
- P0 static/SQL/architecture regression: **11/11 PASS**
- PostgreSQL canonical SQL `01~24` clean database 순차 실행: **24/24 PASS**
- P0 Java Core 기존 파일: **239/239 SHA-256 exact**
- P0 SQL `01~22`: **22/22 SHA-256 exact**
- failure / error / skip: **0 / 0 / 0**

## 검증 방식

- 단일 장시간 Gradle `test` 대신 DB reset과 Spring context를 안정적으로 격리하기 위해 테스트군을 분리 실행했다.
- 중복 클래스는 제외하고 XML 결과를 합산했으며, 모든 실제 테스트 클래스와 메서드를 포함했다.
- PostgreSQL은 빈 DB 생성 후 SQL `01`부터 `24`까지 `ON_ERROR_STOP=1`로 적용했다.
- P0 기준본과의 파일 해시는 별도 독립 스크립트로 재검증했다.

## 보완 완료

- canonical record 직렬화/fingerprint 경계
- Gradle P1 task 구성
- timestamp cast
- 정책 버전 명칭
- profile effective date·dedupe·conflict validation
- diversity movement bound와 fingerprint metadata
- transaction rollback·append-only 실행 ID
- 인증된 explicit preference write path와 DB 권한 경계

## 최종 게이트

- 기존 P0 정책·replay·core source 불변: PASS
- 행동 프로필 결정론: PASS
- 신규 정책 병렬화와 선택 이유 추적: PASS
- popularity/low-exposure/diversity 품질 보정: PASS
- SHADOW/CANARY 비교 및 rollback: PASS
- PostgreSQL append-only·권한·binding: PASS
- P1 상태: **CLOSED**
- 다음 허용 단계: **P2 통계 검증·출시 판정**

## 비차단 경고

- Gradle 9 호환성 deprecation warning이 남아 있다. 현재 Gradle 8.14.5 검증 결과에는 영향이 없으며 별도 빌드 도구 개선 항목이다.

## 독립 재검증 보강 — 2026-07-19

사용자 재시도 요청에 따라 기존 PASS 기록을 신뢰하지 않고 동일 산출물을 다시 검증했다.

- 사용자 제공 Gradle 8.14.5: P1 core 17개 시나리오 PASS
- Java Core Wave 5~7, golden fixture, framework isolation: PASS
- P1 static/mode/mapper + P0 verification: PASS
- 외부 PostgreSQL 15.18 연결 기반 preference/profile/SHADOW/CANARY integration: PASS
- 빈 PostgreSQL DB에서 canonical SQL `01~24` 순차 적용: PASS
- 재검증 중 최초 integration 실패 원인은 Docker 미제공 환경에서 Testcontainers fallback이 선택된 실행 설정 문제였으며, 사용자 제공 PostgreSQL 15 외부 URL로 재실행하여 정상 PASS를 확인했다. 소스 결함은 아니므로 소스 변경은 수행하지 않았다.

재검증 증거:

- `verification/P1_RECHECK_CORE.log`
- `verification/P1_RECHECK_CONTRACT_P0.log`
- `verification/P1_RECHECK_INTEGRATION.log`
- `verification/P1_RECHECK_POSTGRES_01_24.log`

최종 판정은 **P1 CLOSED 유지**다.
