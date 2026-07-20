# IP-8 Controlled Hook Change Proposal

## 1. 상태

| 항목 | 값 |
|---|---|
| 문서 ID | `ip-8-controlled-hook-change-proposal-v1` |
| 계약 성격 | `PROPOSAL_ONLY` |
| 적용 여부 | `NOT APPLIED` |
| 추천 위치 | `PostController.explore`의 legacy service 성공 반환 직후 |
| production activation | `PROHIBITED` |
| response authority | `legacy` |

## 2. 후보 평가 결론

권장 위치는 Controller return boundary다.

- Service의 read-only DB transaction 종료 후 실행 가능
- legacy response가 먼저 확정됨
- hook return value를 사용하지 않을 수 있음
- hook 자체를 no-op bean으로 교체하기 쉬움
- rollback Level 2~4가 명확함

Service 내부 hook은 transaction 점유와 latency 결합 위험 때문에 채택하지 않는다.

## 3. 제안 대상

### 기존 protected file

- `jc-backend/src/main/java/com/jc/backend/post/PostController.java`

### 후속 단계에서 생성할 후보

- `com.jc.backend.search.shadow.SearchShadowBackendConfiguration`
- `com.jc.backend.search.shadow.ExploreShadowHookRequestFactory`
- disabled/no-op bean contract test
- Controller disabled-equivalence Spring test

### build 후보

- backend가 `:jc-search-shadow-wiring`을 참조하는 최소 dependency
- production config에는 enable 값 추가 금지

## 4. 삽입 전

```java
return ApiResponse.ok(postService.explore(keyword, region, pageable));
```

## 5. illustrative pseudo-diff — 적용 금지

```diff
- return ApiResponse.ok(postService.explore(keyword, region, pageable));
+ PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);
+ try {
+     searchShadowHook.dispatch(
+         exploreShadowHookRequestFactory.create(keyword, region, pageable, legacyResponse));
+ } catch (RuntimeException ignored) {
+     // Concrete implementation must emit only safe non-persistent diagnostics.
+     // Legacy response and HTTP status remain authoritative.
+ }
+ return ApiResponse.ok(legacyResponse);
```

이 diff는 설명용이며 IP-8 source에 적용되지 않았다.

## 6. 필수 wiring invariants

- `searchShadowHook` 반환값을 response에 사용하지 않음
- default bean은 `NoOpSearchShadowHook`
- missing/invalid property는 disabled
- default sample = 0
- production profile enable 없음
- bounded executor 없으면 dispatch skip
- hook exception·timeout·rejection·queue full·logging failure 완전 격리
- raw query/ID/payload logging 금지
- compatibility output을 runtime candidate로 사용 금지

## 7. property/profile proposal

실제 key는 후속 System Coordination 승인을 받아야 한다. IP-8은 production property를 추가하지 않는다.

개념 계약:

```text
mode = disabled
sampleBasisPoints = 0
explicitAllow = false
activeProfile must not be prod/default
```

Test-only profile 후보는 기존 IP-7 계약의 `search-shadow-test`다.

## 8. Rollback

- L0 sample 0
- L1 mode disabled
- L2 no-op bean
- L3 Controller hook call 제거
- L4 backend module dependency 제거

L0/L1은 config capability가 승인된 뒤에만 무배포가 가능하다. Remote config provider는 IP-8에서 연결하지 않았다.

## 9. 승인 전 필수 테스트

1. disabled Controller response identity/value/serialization/HTTP status exact
2. exception mapping exact
3. executor/runtime/log invocations zero
4. hook failure/timeout/rejection/queue full/circuit/logging failure response unchanged
5. full `:test`
6. `p0Verification`, `p1Verification`, `p2Verification`
7. `ip8SearchRegressionClosure`
8. protected source/canonical SQL re-baseline approval

## 10. 현재 판정

```text
Proposal completeness: COMPLETE
Source application: NONE
Approval: PENDING
IP-9 implementation: HOLD
```
