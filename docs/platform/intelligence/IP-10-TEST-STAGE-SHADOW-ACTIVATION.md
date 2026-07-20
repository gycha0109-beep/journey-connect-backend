# IP-10 Test/Stage Search Shadow Activation

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-10-test-stage-shadow-activation-v1` |
| 상태 | `IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| production shadow | `DISABLED / PROHIBITED` |
| response authority | `legacy` |
| DB/SQL 영향 | 없음 |

## 목적과 비목적

IP-10은 IP-9의 `/api/v1/explore` controlled hook 뒤에 **명시적으로 선택된 test/stage context에서만** Search Runtime을 실제 실행하는 조립을 추가한다. Search 결과는 legacy HTTP 응답에 사용하지 않는다. production 활성화, API cutover, persistence, exposure, release gate, production cursor는 비범위다.

## 실제 시작 기준선

```text
PostController.explore
→ PostService.explore 1회
→ legacy PageResponse 확정
→ ExploreSearchShadowBridge.afterExplore
→ 동일 legacy PageResponse를 ApiResponse.ok로 반환
```

IP-10은 Controller를 추가 수정하지 않았다. IP-9 hook 위치와 response authority를 그대로 사용한다.

## Bean graph

### Default / production-equivalent

- `SearchShadowBackendConfiguration`
- `DisabledExploreSearchShadowBridge` 단일 bean
- executor/provider/runtime/evidence bean 없음
- 설정 누락·공백·unknown 값은 disabled

### Explicit test/stage

활성 조건은 모두 충족해야 한다.

1. active profile이 `search-shadow-test` 또는 `search-shadow-stage`
2. `prod`/`production` profile이 동시에 존재하지 않음
3. `search.shadow.stage.mode=test_only`
4. `search.shadow.stage.explicit-allow=true`
5. 검증된 bounded 설정

활성 graph:

```text
DefaultExploreSearchShadowBridge
→ StageExploreSearchShadowHook
→ StageSearchShadowTaskExecutor
→ SearchShadowIntegrationBoundary
→ StageBoundedSearchShadowExecutionPort
→ DefaultSearchRuntime
→ InMemoryStageSearchCatalog
→ IP-6 comparison
→ InMemoryStageSearchShadowComparisonLogPort
```

기본 sampling은 `0` basis points다. test fixture만 `10000`으로 override한다.

## Runtime input source

`DefaultStageExploreSearchRuntimeInputProviderFactory`는 legacy **request**만 SearchRequest로 변환한다. legacy response item은 candidate source로 사용하지 않는다. retrieval/ranking은 synthetic `InMemoryStageSearchCatalog`가 담당한다.

- first page만 지원
- explicit legacy sort는 unsupported
- invalid page/size는 invalid
- region은 SearchFilter로 변환
- raw query와 normalized query는 IP-3 canonicalizer로 분리
- user identity, production cursor, persistence owner를 발명하지 않음

## Executor·queue·timeout

Test/stage 기본값:

| 항목 | 값 |
|---|---:|
| sample | `0 bps` |
| dispatch max concurrency | `2` |
| dispatch queue | `8` |
| runtime max concurrency | `2` |
| runtime queue | `8` |
| runtime timeout | `200 ms` |

이는 production budget이 아니다. 모든 queue는 bounded이며 daemon named thread를 사용한다. queue full/rejection/shutdown/timeout/cancellation은 shadow만 중단하고 legacy 응답에는 영향을 주지 않는다.

## Comparison·evidence

IP-6/IP-7의 comparison 및 structured record를 재사용한다. evidence는 bounded memory-only recorder에만 저장하며 persistence/exposure/release authority가 없다.

금지 데이터:

- raw query
- raw correlation/session/user ID
- 전체 legacy/Search payload
- 인증 토큰
- stack trace 포함 민감 request

## 변경 파일

- backend stage package production Java 15개
- stage JUnit test 5개
- IP-10 static test 1개
- Search wiring additive 변경 3개
- backend build task 2개
- default disabled condition 1개
- 문서·verification evidence

## 보호 불변조건

- PostService 호출 1회
- 동일 legacy response object 반환
- Search 결과로 count/order/page 변경 금지
- request thread는 shadow completion을 기다리지 않음
- default/prod active bean 없음
- production config 변경 없음
- DB/SQL/Recommendation 변경 없음
