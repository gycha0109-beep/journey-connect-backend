# Stage 0·Stage 1 첫 배치 구현 보고서

## 완료 사항

### Stage 0 — TypeScript 기준선 동결

- `reference/recommendation-ts-2.9b`에 원본 구현 418개 파일 보관
- 원본 archive SHA-256 기록
- 파일별 SHA-256 manifest 생성
- 추가·삭제·변조 탐지 스크립트 추가
- 복사된 참조본에서 `npm ci`, typecheck, lint, 963 tests 재검증
- 참조 구현 수정 금지 정책 문서화

### Stage 1 — Java 추천 코어 골격과 foundation 이식

- 독립 `jc-recommendation-core` Gradle 모듈 생성
- `jc-backend → jc-recommendation-core` 단방향 의존 연결
- Spring/JPA/DB 의존 금지 검사 추가
- Java 21 UTF-8 및 `-Xlint:all -Werror` 검증
- entity/feature/event 기반 enum·record 추가
- feature vocabulary v1 42개 이식
- 기반 정책 7종 이식
- candidate limit 이식
- repeat/time decay 이식
- saturation 이식
- explicit preference signal builder 이식
- dependency-free Java contract test 추가
- TS↔Java IEEE-754 exact oracle 비교 추가
- 전용 GitHub Actions workflow 추가

## 변경된 기존 파일

- `.gitignore`
- `jc-backend/settings.gradle.kts`
- `jc-backend/build.gradle.kts`

기존 비즈니스 코드, Flyway migration, 프론트엔드 코드는 변경하지 않았다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| 참조 checksum 418개 | PASS |
| 참조 TypeScript typecheck | PASS |
| 참조 TypeScript lint | PASS |
| 참조 TypeScript 테스트 | 65 files / 963 tests PASS |
| Java main/test `javac --release 21` | PASS |
| Java `-Xlint:all -Werror` | PASS |
| Java foundation contract | PASS |
| TS↔Java foundation oracle | EXACT MATCH |
| 신규 workflow YAML parse | PASS |
| 신규 파일 trailing whitespace | PASS |

## 검증 제한

전체 `jc-backend` Gradle 테스트는 실행 환경에서 `services.gradle.org` DNS 접근이 차단되어 Gradle 8.14.5 wrapper 배포본을 내려받지 못했다.

```text
java.net.UnknownHostException: services.gradle.org
```

따라서 backend의 전체 Spring compile/test는 CI 또는 Gradle 배포본이 이미 설치된 개발 환경에서 반드시 다시 수행해야 한다. 순수 Java 추천 코어는 Gradle 없이 `javac`로 독립 검증했다.

## 다음 작업

1. Wave 1 나머지 event/context/profile/result 계약 이식
2. 정식 golden fixture exporter 생성
3. state-event resolution 포팅
4. interest/freshness/popularity 입력·결과 타입 이식
5. Wave 2 scoring exact-equivalence 시작

P0 DB·API 작업은 아직 시작하지 않는다. Java core 전체 동등성 게이트 통과가 선행 조건이다.
