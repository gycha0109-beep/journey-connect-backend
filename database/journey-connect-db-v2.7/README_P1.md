# Journey Connect DB v2.6 — P1

## 증분

- `23_recommendation_p1_profile_policy.sql`
  - P1 feature tag seed
  - authenticated explicit preference current-state table와 secure atomic replace 함수
  - append-only behavior profile snapshot
  - append-only policy assignment
  - append-only baseline/treatment comparison
  - run/user/session/profile binding trigger
  - recommendation role 최소 권한
- `24_recommendation_p1_profile_policy_smoke_test.sql`
  - constraints, grants, secure function, append-only, binding 검증

## 적용 순서

기존 검증된 SQL `01~22` 뒤에 `23`, `24`를 적용한다. `01~22`는 v2.5 기준본과 SHA-256 exact를 유지한다.
