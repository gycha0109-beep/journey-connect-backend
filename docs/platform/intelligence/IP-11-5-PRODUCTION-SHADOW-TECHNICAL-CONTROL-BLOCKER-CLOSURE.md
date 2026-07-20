# IP-11.5 Production Shadow Technical Control Blocker Closure

## 상태

`TECHNICAL_CONTROLS_IMPLEMENTED / EXTERNAL_ATTESTATION_PENDING`

## 목적

IP-11 `NO_GO` 중 코드로 닫을 수 있는 projection, eligibility, provider, kill-switch, cohort, sampling, resource, observability, evidence, disable-drill capability를 구현한다. 사람·조직 승인은 대신하지 않는다.

## 실제 변경

- 신규 모듈 `jc-search-production-controls`
- backend capability adapter `com.jc.backend.search.shadow.production`
- migration `27_search_document_projection.sql`, smoke `28_search_document_projection_smoke_test.sql`
- backend JUnit 4개와 Gradle task 4개
- IP-8 readiness의 `26개 고정` 테스트를 `01..26 exact + 27+ extension`으로 최소 보정

## Dependency graph

```text
jc-backend
  -> jc-search-production-controls
     -> jc-search-shadow-wiring
        -> jc-search-integration/runtime/compatibility/contracts

PostController -> IP-9 ExploreSearchShadowBridge (unchanged)
IP-11.5 capability graph -> no active production bridge
SQL projector -> projection table -> JDBC read-only store -> Search Runtime
```

## 실행 순서 계약

```text
legacy response fixed
-> backend bridge (unchanged)
-> production capability remains absent from active bridge
future technical path: guard -> kill -> cohort -> sample -> provider -> bounded executor
-> Search Runtime -> comparison/evidence capability
-> original legacy object returned unchanged
```

## Migration 선택

별도 projection table과 명시적 rebuild/project function을 채택했다. PostService transaction에 trigger나 동기 projector를 추가하지 않았다. Writer failure는 legacy write transaction과 분리된다.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
