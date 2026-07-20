# Stage 2 Wave 3 — Ranking·Diversity·Exploration Java 포팅 구현 보고서

## 1. 배치 목적

이 배치는 TypeScript Phase 2.9b 참조 구현의 랭킹 계열 primitive를 Java 21 순수 코어로 이식하고, 언어 간 결과를 canonical output과 IEEE-754 raw bits로 대조하는 것을 목적으로 한다.

P0 저장·이벤트·Spring 통합은 시작하지 않았다.

## 2. 구현 범위

### Base ranking

- `CandidateRanker`
- ranking policy v1
- complete comparator
- terminal audit
- cursor v1 encode/decode
- stable pagination boundary

### Diversity

- diversity policy v1
- exact metadata coverage
- strict diversity selection
- ordered relaxation
- promotion/demotion movement bound
- missing metadata 계측
- score·identity·sort-key provenance 보존

### Seeded exploration

- exploration policy v1
- FNV-1a 32-bit UTF-8 seed
- seed material NUL separator contract
- freshness/popularity quality evidence
- recent exposure and quality filtering
- deterministic pool order
- rank 6·16 insertion
- all affected diversity-window guard
- slot decision provenance
- inserted candidate score impersonation 방지
- hard-excluded resurrection 방지
- personalized identity and terminal partition invariant

## 3. 신규 검증 자산

### 계약 테스트

- `CoreWave3RankingDiversityContractTest`
- `CoreWave3ExplorationContractTest`

### TypeScript golden exporter

- `export_wave3_base_ranking_golden.mjs`
- `export_wave3_diversity_golden.mjs`
- `export_wave3_exploration_golden.mjs`

### Java golden oracle

- `CoreWave3BaseRankingGoldenOracle`
- `CoreWave3DiversityGoldenOracle`
- `CoreWave3ExplorationGoldenOracle`

### committed golden

- `wave3-base-ranking-v1.json`
- `wave3-diversity-v1.json`
- `wave3-exploration-v1.json`

### 통합 게이트

```bash
bash scripts/recommendation/verify_java_core_wave3.sh
```

Node exporter는 병렬 실행하여 전체 Foundation~Wave 3 exact 검증 시간을 줄였다.

## 4. 검증 결과

| 검증 | 결과 |
|---|---|
| Java 21 main/test compile | PASS |
| `-Xlint:all -Werror` | PASS |
| framework dependency scan | PASS |
| Foundation oracle | EXACT MATCH |
| Wave 1 golden | EXACT MATCH |
| Wave 2 scoring golden | EXACT MATCH |
| Wave 3 base ranking golden | EXACT MATCH |
| Wave 3 diversity golden | EXACT MATCH |
| Wave 3 exploration golden | EXACT MATCH |
| committed golden regression | PASS |

현재 순수 Java 코어 규모:

- main Java source: 153개
- test/oracle Java source: 11개

## 5. 주요 호환성 보존 사항

### Signed zero

랭킹 결과의 원본 score `-0.0`은 raw bits 그대로 보존한다. 비교용 sort key만 TypeScript와 동일하게 `+0.0`으로 canonicalize한다.

### UTF-16 문자열 정렬

Java 기본 locale 정렬이 아니라 TypeScript `<` 비교와 동일한 UTF-16 code-unit comparator를 사용한다.

### Seeded tie-break

Java 표준 난수는 사용하지 않는다. TypeScript의 UTF-8 byte 기반 FNV-1a 32-bit unsigned 결과를 `long`으로 보존한다.

### Exploration partition

삽입된 exploration 후보만 terminal 집합에서 제거한다. 기존 personalized 후보는 제거하지 않으며 hard-excluded 후보를 부활시키지 않는다.

## 6. 자체 리뷰 보완

- 직접 exploration에서 임의로 약식 검증하지 않도록 기존 `DiversityContracts.validatePolicy()`를 재사용 가능하게 공개했다.
- full verifier가 하위 verifier를 순차 중첩 실행해 장시간 소요되던 구조를 제거했다.
- Java는 한 번만 컴파일하고, TypeScript exporter 6종을 병렬 실행하도록 통합 게이트를 재구성했다.
- exploration 결과 모델의 nullable composition mode도 문자열이 아닌 `ScoreCompositionMode`로 타입 고정했다.

## 7. 현재 단계와 다음 작업

```text
Stage 0  완료
Stage 1  완료
Stage 2  진행 중 — scoring 및 ranking/diversity/exploration primitive 완료
P0       미진입
P1/P2    미진입
```

다음 배치는 다음 순서로 진행한다.

1. ranking-v2 integration
2. cursor v2 exact contract
3. ranking-v3 integration
4. cursor v3 exact contract
5. full ranking pipeline golden 및 partition invariant
6. exposure·attribution 포팅

## 8. 전체 프로젝트 검증 제한사항

순수 Java 추천 코어와 TypeScript 참조 구현은 본 보고서의 검증 게이트를 모두 통과했다.

다만 전체 Spring Boot 백엔드 Gradle 테스트는 현재 실행 환경에서 Gradle wrapper 배포본을 내려받지 못해 완료하지 못했다.

```text
java.net.UnknownHostException: services.gradle.org
```

`./gradlew`의 실행 권한 문제는 `bash ./gradlew`로 우회했지만, 외부 Gradle 배포 서버 DNS 접근 제한은 우회하지 않았다. 따라서 다음 검증은 Gradle 배포본이 준비된 개발 환경 또는 CI에서 추가로 수행해야 한다.

```bash
cd jc-backend
bash ./gradlew test --no-daemon
```

이 제한은 숨기지 않으며, 현재 완료 판정은 다음 범위로 한정한다.

- TypeScript 참조 구현 전체 회귀
- 순수 Java 21 컴파일과 계약 테스트
- TypeScript ↔ Java exact golden 비교
- 참조 소스 SHA-256 무결성
- 추천 Java 포팅 전용 CI workflow 문법

전체 Spring 애플리케이션 통합 성공은 아직 주장하지 않는다. P0 진입 전 반드시 백엔드 전체 테스트를 통과해야 한다.
