# SR-6F-I Search CTR Platform-neutral Stage Readiness Handoff

## 상태

```text
Stage: SR-6F-I
Metric: search-click-through-rate-v1
Predecessor: SR-6F-H
H execution-control implementation: VERIFIED
Readiness-handoff implementation: VERIFIED
Verified implementation head: 3c04b72dadde0931b68154d86c82d5b71add9958
Actual stage execution: NOT_PERFORMED
Readiness contract: search-ctr-stage-readiness-handoff-v1
Readiness status: BLOCKED_PLATFORM_UNDECIDED
Final deployment platform: UNDECIDED
Resource creation authorization: NO
Billing spend authorization: NO
IAM mutation authorization: NO
SR-6F-H execution status: BLOCKED_EXTERNAL_STAGE_ACCESS
Finality write: DISABLED
Merge/deploy: NOT_PERFORMED
Overall: VERIFIED_READINESS_HANDOFF_HOLD_PLATFORM_SELECTION
```

## 목적

SR-6F-H는 실제 stage one-shot을 안전하게 수행할 수 있는 제어 경로까지 검증했다. 그러나 현재 외부 운영 기준선에는 확정된 배포 플랫폼, stage resource, endpoint, credential, 비용 승인, IAM 승인, operator가 없다.

SR-6F-I는 이 외부 공백을 코드나 문장으로 추정하지 않는다. 실제 실행 재개에 필요한 증거를 platform-neutral manifest로 고정하고, 해당 manifest가 authoritative OP-3 decision matrix와 일치하는지 기계 검증한다.

이번 단계는 infrastructure provisioning 단계가 아니다.

## Authoritative platform source

```text
verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json
```

현재 authoritative 값은 다음과 같다.

```text
DECISION_STATUS=DEFERRED_PLATFORM_UNDECIDED
FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
DEPLOYMENT_IMPLEMENTATION=DEFERRED
CLOUD_RESOURCE_CREATION_AUTHORIZED=false
BILLING_SPEND_AUTHORIZED=false
IAM_MUTATION_AUTHORIZED=false
PAID_CLOUD_USAGE=FORBIDDEN
COST_CEILING=0
```

과거 GCP Cloud Run 설계는 현재 matrix에서 reference-only다. AWS도 학원 커리큘럼 확인 전에는 선택된 platform이 아니다. SR-6F-I는 어느 쪽도 실제 stage authority로 승격하지 않는다.

## 구현 패키지

```text
operations/search-ctr/sr6fi/
├─ README.md
├─ stage-readiness-manifest.env.example
├─ required-secret-names.txt
├─ verify_stage_readiness.py
├─ render_sr6fh_binding.py
└─ test_verify_stage_readiness.py
```

Java 정적 계약:

```text
jc-backend/src/test/java/com/jc/backend/intelligence/search/
└─ SearchCtrStageReadinessHandoffContractTest.java
```

## Manifest boundary

현재 template는 반드시 다음 상태를 유지한다.

```text
SR6FI_READINESS_STATUS=BLOCKED_PLATFORM_UNDECIDED
SR6FI_FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
SR6FI_RESOURCE_CREATION_AUTHORIZED=NO
SR6FI_BILLING_SPEND_AUTHORIZED=NO
SR6FI_IAM_MUTATION_AUTHORIZED=NO
SR6FI_STAGE_ENDPOINT_SHA256=UNASSIGNED
SR6FI_SR6FH_CONTRACT_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS
```

manifest에는 raw endpoint나 credential을 넣지 않는다. endpoint는 미래에도 SHA-256 fingerprint만 기록한다.

## Required evidence classes

Ready 전환에는 다음 evidence class가 모두 필요하다.

1. authoritative final platform decision
2. exact deployed source SHA
3. immutable deployment artifact digest
4. platform deployment resource ID
5. stage database platform/resource ID
6. stage endpoint SHA-256 fingerprint
7. reviewed `journey-connect-db-v2.8` package digest
8. actual DB deployment evidence digest
9. GitHub `stage` environment protection evidence
10. required secret names-only inventory evidence
11. execution operator
12. immediate revoke operator
13. independent approver
14. incident owner/on-call
15. cost owner
16. teardown operator and exact UTC deadline
17. evidence store resource ID and retention days

Secret values, endpoint strings, raw database rows, and user identity are not readiness evidence.

## Fail-closed verifier

`verify_stage_readiness.py`는 template와 ready 두 모드를 제공한다.

Template mode는 authoritative matrix가 `UNDECIDED`인지, 모든 외부 값이 `UNASSIGNED/UNVERIFIED`인지, resource/billing/IAM authorization이 `NO`인지, traffic 0·serving forbidden·finality disabled인지 검증한다. endpoint/URI/secret-like material이 들어오면 실패한다.

Ready mode는 authoritative platform selection과 execution authorization이 완료된 후에만 통과한다. source SHA·artifact digest·endpoint fingerprint·DB package digest·operator·evidence·retention·teardown 값을 전부 검증하며, deployed DB package digest는 reviewed source package digest와 정확히 같아야 한다.

현재 matrix에서는 ready mode가 반드시 실패한다.

## Secret boundary

`required-secret-names.txt`는 SR-6F-H workflow가 요구하는 secret 이름만 포함한다.

```text
SR6FH_STAGE_ADMIN_DATABASE_URL
SR6FH_STAGE_ADMIN_USERNAME
SR6FH_STAGE_ADMIN_PASSWORD
SR6FH_STAGE_BACKEND_JDBC_URL
SR6FH_STAGE_BACKEND_USERNAME
SR6FH_STAGE_BACKEND_PASSWORD
SR6FH_STAGE_JWT_SECRET
```

값은 읽거나 기록하지 않는다. names-only inventory digest는 다음으로 고정됐다.

```text
sha256:5bd0848027faacefc1b3b4763616b7aefbf025e99b3a49ca93c7de9978994aad
```

## Review-only H binding

`render_sr6fh_binding.py`는 ready manifest에서 hash와 non-secret identifier만 추출해 별도 output directory에 proposal을 만든다.

```text
SR6FH_EXECUTION_STATUS=READY_FOR_ONE_SHOT
SR6FH_AUTHORIZED_STAGE_ENDPOINT_SHA256=<fingerprint>
SR6FH_BOUND_DEPLOYMENT_SOURCE_SHA=<sha>
SR6FH_BOUND_DEPLOYMENT_ARTIFACT_DIGEST=<digest>
SR6FH_BOUND_DB_PACKAGE_SHA256=<digest>
SR6FH_BOUND_DB_DEPLOYMENT_EVIDENCE_SHA256=<digest>
SR6FH_FINALITY_WRITE=DISABLED
SR6FH_BINDING_REVIEW_STATUS=PROPOSED_NOT_AUTHORIZED
```

이 도구는 authoritative H contract를 수정하지 않는다. proposal 출력은 execution authorization이 아니다.

## 검증

| Gate | Result |
|---|---|
| Python syntax | SUCCESS |
| Python readiness regression | SUCCESS — 4 tests |
| Current blocked template verification | SUCCESS |
| Raw endpoint/secret rejection | SUCCESS |
| Review-only future-ready fixture | SUCCESS |
| Operations-only Java entry point compile | SUCCESS |
| Search focused PostgreSQL | SUCCESS — 31 suites / 116 tests / failures 0 / errors 0 / skipped 0 |
| Disposable PostgreSQL H round-trip | SUCCESS |
| Protected recommendation contracts | SUCCESS — P1 17 / P2 23 scenarios |
| Full backend regression | SUCCESS — 110 suites / 390 tests / failures 0 / errors 0 / skipped 0 |
| PostgreSQL 15 canonical integration | SUCCESS |
| PostgreSQL 18 canonical integration | SUCCESS |
| Backend IP-12.5 protected readiness | SUCCESS |

Verified implementation-head CI:

```text
SR Search Recommendation: 31137267367
Recommendation P0 Database CI: 31137268018
Backend PR CI: 31137267557
```

Implementation-head evidence digests:

```text
Focused: sha256:bf3b3fa3d9c57c17f48e2804c73ea1729ff2cd781235c4903aa0e9213f9dde4a
Full regression: sha256:0abd5b3dc8761a570f75719c7b4c401c09ea33f287c42869865676e09e0b9b54
```

초기 implementation head `af73712e6c560818613c08c142410a17127a765d`에서는 기존 governance test가 H 단계의 workflow step 이름을 고정해 focused suite 1건이 실패했다. 권한 경계를 완화하지 않고 H base·I readiness verifier·I focused step까지 확인하도록 contract를 갱신한 뒤 `3c04b72dadde0931b68154d86c82d5b71add9958`에서 전체 검증에 성공했다.

## 수행하지 않은 것

```text
Final platform selection: NOT_PERFORMED
Cloud resource creation: NOT_PERFORMED
Billing linkage/spend: NOT_PERFORMED
IAM mutation: NOT_PERFORMED
Stage endpoint binding: NOT_PERFORMED
Credential creation/read: NOT_PERFORMED
Stage deployment: NOT_PERFORMED
Stage DB package application: NOT_PERFORMED
Actual stage preflight: NOT_PERFORMED
Actual membership grant: NOT_PERFORMED
Actual one-shot execution: NOT_PERFORMED
Actual projection write: NOT_PERFORMED
Merge: NOT_PERFORMED
Production activation: NOT_PERFORMED
SETTLED finality writer: NOT_IMPLEMENTED
```

## 다음 gate

다음 상태 전환은 repository 구현만으로 발생하지 않는다.

```text
FINAL_PLATFORM_SELECTED_AND_FUNDED_EXECUTION_SEPARATELY_AUTHORIZED
```

그 증거가 authoritative OP-3 matrix에 반영된 후, SR-6F-I ready manifest 검증과 review-only H binding proposal 생성으로 재개한다.
