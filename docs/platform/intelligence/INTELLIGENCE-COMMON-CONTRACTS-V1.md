# Intelligence Common Contracts V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `intelligence-common-contracts-v1` |
| 상태 | `ACTIVE DESIGN` |
| 소유 트랙 | Intelligence Platform |
| JSON naming | camelCase |
| wire enum | lowercase snake_case |
| time | `Instant` / `TIMESTAMPTZ` / UTC ISO-8601 `Z` |
| hash | SHA-256 lowercase hex, versioned canonicalization |

## 2. 적용 원칙

이 문서의 객체는 논리 계약이다. 하나의 공통 DB table 또는 하나의 Java superclass를 강제하지 않는다.

- 도메인 객체는 composition 또는 adapter로 공통 필드를 충족할 수 있다.
- 현재 추천 객체를 in-place migration하지 않는다.
- 동일 version에서 field 의미를 변경하지 않는다.
- 현재 구현과 일치하지 않는 필드는 compatibility adapter에서 생성하거나 nullable 조건을 명시한다.
- domain extension은 별도 versioned payload로 둔다.

## 3. 공통 식별자

### 3.1 `runId`

- 실행 증거의 유일 ID
- 최대 128자
- `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`
- run type을 문자열 parsing으로 추론하지 않는다.

### 3.2 `entityRef`

```text
<entity-type>:<source-id>
```

System Contract의 registry를 따른다. 현재 recommendation P0 post numeric string 호환성 예외는 adapter 밖으로 전파하지 않는다.

### 3.3 `subjectRef`

허용 identity scheme은 명시적으로 기록한다.

| scheme | 예시 | 상태 |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | 신규 Data/Intelligence 기본 |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | 현재 P2 보호 예외 |
| anonymous session | subject null + server-derived session | taxonomy/도메인 허용 시만 |

서로 다른 scheme은 restricted mapping 없이 동일 subject로 취급하지 않는다.

### 3.4 추적 ID

- `requestId`: API/application request
- `correlationId`: 상위 흐름
- `operationId`: Operations action
- `experimentId`, `experimentVersion`, `assignmentId`: Reliability contract

필드가 null이어도 JSON field 존재를 요구할지는 도메인 schema가 결정한다.

## 4. `IntelligenceRun`

### 4.1 목적

한 번의 Intelligence 실행이 어떤 immutable input과 version을 사용해 어떤 terminal output을 만들었는지 증명한다.

V1은 terminal evidence다. `queued`/`running` lifecycle을 동일 row에서 update하는 계약이 아니다.

### 4.2 필드

| 필드 | 필수 | 의미 |
|---|---:|---|
| `runId` | 예 | 실행 유일 ID |
| `runType` | 예 | `recommendation`, `search`, `content_analysis`, `trip_planning` |
| `runSchemaVersion` | 예 | `intelligence-run-v1` |
| `requestId` | 조건부 | request 기반 실행이면 필수 |
| `correlationId` | 조건부 | 상위 흐름이 있으면 필수 |
| `operationId` | 아니오 | 운영자 조치로 생성된 경우 |
| `experimentId` | 아니오 | experiment에 결속된 경우 |
| `experimentVersion` | 조건부 | experimentId가 있으면 필수 |
| `assignmentId` | 아니오 | resolved assignment가 있으면 기록 |
| `subjectRef` | 조건부 | 개인화/사용자 실행이면 필수 |
| `identityScheme` | 조건부 | subjectRef가 있으면 필수 |
| `entityRef` | 아니오 | 단일 대상 분석 등 |
| `surface` | 아니오 | UI/runtime surface |
| `referenceTime` | 예 | 결정론적 계산 기준 시각 |
| `inputSnapshotId` | 예 | immutable input |
| `candidateSnapshotId` | 아니오 | candidate stage가 있으면 |
| `outputSnapshotId` | 조건부 | succeeded/partial/fallback이면 필수 |
| `explanationSnapshotId` | 아니오 | explanation 생성 시 |
| `policyVersion` | 조건부 | policy 기반 실행이면 필수 |
| `featureDefinitionVersion` | 조건부 | feature 소비 시 필수 |
| `modelVersion` | 아니오 | model 사용 시 필수 |
| `promptVersion` | 아니오 | prompt 사용 시 필수 |
| `metricDefinitionVersion` | 아니오 | evaluation hook 결속 시 |
| `producerBuildId` | 예 | 실행 build |
| `dependencySnapshotRefs` | 예 | 빈 배열 허용, provider/dataset refs |
| `deterministicSeed` | 아니오 | seed 기반 처리 시 |
| `nondeterminismDescriptor` | 아니오 | model/provider 비결정성 정보 |
| `startedAt` | 예 | 실행 시작 |
| `completedAt` | 예 | terminal 완료 |
| `status` | 예 | terminal status |
| `fallbackCode` | 조건부 | status=`fallback`이면 필수 |
| `failureCode` | 조건부 | status=`failed`이면 필수 |
| `replayClass` | 예 | exact/semantic/evidence |
| `runFingerprint` | 예 | versioned canonical run evidence hash |

### 4.3 status

```text
succeeded
partial
fallback
failed
```

- 결과 0건은 자동 실패가 아니다. Search no-result는 정상 `succeeded`일 수 있다.
- `fallback`은 의도된 대체 경로가 결과를 제공한 경우다.
- `partial`은 일부 도메인 output이 유효하나 완전성을 충족하지 못한 경우다.
- 내부 예외 문자열을 failureCode로 사용하지 않는다.

### 4.4 불변조건

1. `startedAt <= completedAt`
2. 성공·partial·fallback은 output snapshot을 가진다.
3. failed는 output snapshot이 없거나 명시적 failure output schema를 가진다.
4. model 사용 시 `modelVersion`, parameters evidence, inference record가 필요하다.
5. prompt 사용 시 `promptVersion`과 prompt template hash가 inference record에 결속된다.
6. 동일 runId로 다른 fingerprint를 저장할 수 없다.
7. run evidence를 update/delete하지 않는다.

### 4.5 예시

```json
{
  "runId": "search-run:9d...",
  "runType": "search",
  "runSchemaVersion": "intelligence-run-v1",
  "requestId": "request:1a...",
  "correlationId": "request:1a...",
  "subjectRef": "subject:7b...",
  "identityScheme": "platform_subject_v1",
  "surface": "home_search",
  "referenceTime": "2026-07-19T05:30:00.000000Z",
  "inputSnapshotId": "search-query-snapshot:...",
  "candidateSnapshotId": "search-candidates:...",
  "outputSnapshotId": "search-result:...",
  "policyVersion": "search-ranking-policy-v1",
  "featureDefinitionVersion": "search-feature-definitions-v1",
  "producerBuildId": "git-sha",
  "dependencySnapshotRefs": ["search-index-snapshot:..."],
  "startedAt": "2026-07-19T05:30:00.001000Z",
  "completedAt": "2026-07-19T05:30:00.045000Z",
  "status": "succeeded",
  "fallbackCode": null,
  "failureCode": null,
  "replayClass": "exact_replay",
  "runFingerprint": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

### 4.6 금지

- policyVersion을 `latest`로 저장
- system clock을 core 내부에서 재조회
- run row를 운영 상태 변화에 맞춰 덮어쓰기
- raw stack trace 또는 자유 텍스트를 run에 저장
- runId prefix로 runType 결정

## 5. `IntelligenceInputSnapshot`

### 5.1 목적

실행이 실제 소비한 canonical input과 source lineage를 immutable하게 고정한다.

### 5.2 필드

| 필드 | 필수 | 의미 |
|---|---:|---|
| `inputSnapshotId` | 예 | snapshot ID |
| `snapshotSchemaVersion` | 예 | domain input schema |
| `canonicalizationVersion` | 예 | bytes 규칙 |
| `referenceTime` | 예 | 계산 기준 |
| `subjectRef` / `identityScheme` | 조건부 | 개인화 input |
| `sourceReferences` | 예 | source rows/events/datasets/provider snapshots |
| `sourceSchemaVersions` | 예 | source별 version |
| `featureAsOf` | 아니오 | feature freshness 기준 |
| `consentContextVersion` | 조건부 | 개인화/민감 feature 사용 시 |
| `privacyClass` | 예 | `public`, `internal`, `pseudonymous`, `restricted` |
| `canonicalPayloadRef` | 예 | bytes 또는 저장 reference |
| `payloadSizeBytes` | 예 | size |
| `contentHash` | 예 | canonical content hash |
| `producerBuildId` | 예 | snapshot producer |

### 5.3 불변조건

- source reference와 version이 누락된 derived feature를 허용하지 않는다.
- provider facts는 조회 시각과 provider/tool version을 가진다.
- canonical bytes와 contentHash가 일치한다.
- 개인정보 최소화와 payload allowlist를 적용한다.
- 과거 snapshot을 재작성하지 않는다.

## 6. `CandidateSnapshot`

### 6.1 목적

추천·검색·일정 생성의 후보 집합과 stable ordering을 증명하면서 도메인 의미를 유지한다.

### 6.2 공통 envelope

| 필드 | 필수 | 의미 |
|---|---:|---|
| `candidateSnapshotId` | 예 | snapshot ID |
| `candidateSchemaVersion` | 예 | domain candidate schema |
| `candidateDomain` | 예 | recommendation/search/trip_planning |
| `referenceTime` | 예 | candidate facts 기준 |
| `retrievalPolicyVersion` | 조건부 | retrieval이 있으면 |
| `sourceSnapshotRefs` | 예 | index/dataset/place facts |
| `candidates` | 예 | stable ordered list |
| `contentHash` | 예 | 전체 candidate set hash |

각 candidate 공통 필드:

- `candidateRef`
- `candidateKind`
- `sourceRef`
- `eligibilityStatus`
- `retrievalRank` nullable
- `baseRank` nullable
- `finalRank` nullable
- `score` nullable
- `confidence` nullable
- `provenanceRefs`
- `domainExtension`

### 6.3 도메인 차이

- recommendation score와 search relevance score를 같은 scale로 해석하지 않는다.
- trip place candidate는 시간·거리·영업 제약 snapshot을 가진다.
- content analysis는 기본적으로 candidate stage가 없을 수 있다.

### 6.4 금지

- DB/JPA row 전체 직렬화
- provider raw response를 schema 없이 그대로 저장
- tie-break가 불명확한 candidate ordering
- score가 없는 후보에 임의 0을 부여해 의미를 왜곡

## 7. `IntelligenceOutputSnapshot`

### 7.1 목적

실행 결과의 순서, score/confidence 의미, constraint/fallback, producer version을 고정한다.

### 7.2 필드

| 필드 | 필수 | 의미 |
|---|---:|---|
| `outputSnapshotId` | 예 | output ID |
| `outputSchemaVersion` | 예 | domain output schema |
| `runId` | 예 | producer run |
| `orderedResults` | 예 | 빈 배열 허용 |
| `scoreSemanticsVersion` | 조건부 | score가 있으면 |
| `confidenceSemanticsVersion` | 조건부 | confidence가 있으면 |
| `explanationRefs` | 예 | 빈 배열 허용 |
| `constraintSummary` | 아니오 | planner/filter 결과 |
| `fallbackResult` | 아니오 | fallback status일 때 |
| `producerBuildId` | 예 | producer |
| `contentHash` | 예 | output hash |

결과 item은 `resultRef`, `rank`, `score/confidence`, `reasonCodes`, `domainExtension`을 가진다.

## 8. `FeatureValue`

### 8.1 목적

feature 이름 충돌과 authority 혼동을 방지한다.

### 8.2 namespace

```text
user.preference.*
user.profile.*
content.fact.*
content.semantic.*
place.fact.*
place.attribute.*
context.request.*
behavior.observation.*
behavior.aggregate.*
model.inference.*
operations.override.*
```

기존 P1 `theme:*`, `activity:*` wire feature는 보호한다. 신규 공통 namespace로 조용히 rename하지 않고 feature adapter/version을 둔다.

### 8.3 필드

| 필드 | 필수 | 의미 |
|---|---:|---|
| `featureId` | 예 | namespace를 포함한 registry ID |
| `featureNamespace` | 예 | 등록 namespace |
| `featureName` | 예 | namespace 내부 이름 |
| `valueType` | 예 | boolean/integer/decimal/string/enum/vector/reference |
| `value` | 예 | schema-valid value |
| `featureClass` | 예 | authority class |
| `observedAt` | 조건부 | 관측 feature |
| `validFrom` | 아니오 | 효력 시작 |
| `validUntil` | 아니오 | 효력 종료 |
| `sourceRef` | 예 | 원천 또는 inference record |
| `definitionVersion` | 예 | 계산/의미 version |
| `confidence` | 아니오 | inference에만, [0,1] |
| `privacyClass` | 예 | privacy classification |
| `snapshotRef` | 예 | feature set snapshot |

### 8.4 featureClass

```text
source_fact
observed_behavior
derived_aggregate
model_inference
operator_override
```

priority는 consumer policy가 명시한다. `operator_override`가 원문 사실을 삭제하는 의미는 아니다.

### 8.5 불변조건

- model inference는 source fact로 승격되지 않는다.
- confidence가 없다는 이유로 1.0으로 간주하지 않는다.
- definitionVersion 변경 없이 계산식을 바꾸지 않는다.
- privacyClass가 높은 feature는 user-facing explanation에 직접 노출하지 않는다.

## 9. `IntelligenceExplanation`

### 9.1 audience 분리

```text
user_facing
operator
evaluation
internal_debug
```

하나의 explanation payload를 모든 audience에 재사용하지 않는다.

### 9.2 필드

- `explanationId`
- `explanationSchemaVersion`
- `runId`
- `audience`
- `reasonCodes`
- `messageKey` 및 allowlisted parameters 또는 structured evidence
- `evidenceRefs`
- `redactionPolicyVersion`
- `privacyClass`
- `modelInferenceRecordId` nullable
- `producerBuildId`
- `contentHash`

### 9.3 규칙

- user-facing explanation은 디버그 로그가 아니다.
- 민감 profile value, raw query, raw prompt, 자유 텍스트 원문을 복제하지 않는다.
- 모델 생성 설명은 model/prompt provenance를 가진다.
- explanation이 ranking 원인을 사후 추측하지 않도록 실제 evidence ref에 결속한다.
- internal debug detail은 외부 API에 반환하지 않는다.

## 10. `ModelInferenceRecord`

### 10.1 적용 대상

- LLM
- classification/regression ML
- embedding
- reranker
- vision model
- tool-augmented generation

### 10.2 필드

| 필드 | 필수 | 의미 |
|---|---:|---|
| `inferenceRecordId` | 예 | inference ID |
| `runId` | 예 | parent run |
| `inferenceType` | 예 | llm/ml/embedding/reranker/vision/tool_augmented |
| `provider` | 예 | provider adapter ID |
| `modelVersion` | 예 | resolved model version |
| `promptVersion` | 조건부 | prompt 사용 시 |
| `promptTemplateHash` | 조건부 | prompt 사용 시 |
| `toolVersions` | 예 | 빈 map 허용 |
| `inputSnapshotId` | 예 | exact input reference |
| `outputSnapshotId` | 조건부 | 성공/partial 시 |
| `parameters` | 예 | temperature, topP, seed 등 allowlist |
| `parametersHash` | 예 | canonical parameters hash |
| `latencyMs` | 예 | nonnegative |
| `usage` | 예 | token/compute units, 미제공 명시 가능 |
| `safetyPolicyVersion` | 예 | safety policy |
| `safetyResult` | 예 | pass/block/partial/error |
| `status` | 예 | succeeded/partial/fallback/failed |
| `failureCode` | 조건부 | failed |
| `fallbackCode` | 조건부 | fallback |
| `providerRequestRef` | 아니오 | restricted audit ref, raw secret 금지 |
| `producerBuildId` | 예 | adapter/build |
| `resultHash` | 조건부 | output 존재 시 |
| `replayClass` | 예 | exact/semantic/evidence |

### 10.3 금지

- provider alias만 기록하고 실제 model version 누락
- prompt 전문을 일반 log에 기록
- API key, auth header, provider raw secret 저장
- model 결과로 source fact overwrite
- provider 재호출을 exact replay라고 주장

## 11. Replay 계약

### 11.1 `exact_replay`

동일 immutable input, code/build, policy, seed, dependency snapshot으로 동일 canonical output hash를 생성한다.

적합 대상:

- deterministic recommendation core
- deterministic query normalization
- fixed index snapshot 기반 stable ranking
- rule-based constraint checks

### 11.2 `semantic_replay`

bit-level output은 달라도 승인된 semantic comparator를 만족한다.

필수:

- comparator version
- tolerated dimensions
- schema validity
- safety/constraint invariants
- mismatch evidence

### 11.3 `evidence_replay`

외부 모델을 다시 호출하지 않고 저장된 input/output/provenance를 이용해 당시 결정과 downstream 평가를 재검증한다.

적합 대상:

- provider model이 더 이상 제공되지 않음
- sampling 비결정성
- external facts가 변경됨

## 12. Version 계약

| version | owner |
|---|---|
| `schemaVersion` | 해당 contract owner |
| `policyVersion` | Intelligence domain |
| `featureDefinitionVersion` | Intelligence, source facts schema는 Data/owner |
| `modelVersion` | Intelligence registry + provider evidence |
| `promptVersion` | Intelligence domain |
| `metricDefinitionVersion` | Reliability |
| `canonicalizationVersion` | snapshot/write owner, System Coordination 승인 대상 |
| `producerBuildId` | producer |

같은 version ID를 다른 의미에 재사용하면 contract violation이다.

## 13. Failure/Fallback code 규칙

- 외부 API error code는 `UPPER_SNAKE_CASE`
- 저장 wire reason code도 안정적 registry ID 사용
- domain namespace 또는 문서 registry로 충돌 방지
- 내부 exception class/message는 code가 아니다.

공통 category:

```text
INPUT_INVALID
DEPENDENCY_UNAVAILABLE
DEPENDENCY_STALE
POLICY_UNAVAILABLE
MODEL_FAILED
SAFETY_BLOCKED
NO_ELIGIBLE_CANDIDATES
CONSTRAINT_UNSATISFIABLE
PERSISTENCE_FAILED
REPLAY_MISMATCH
```

도메인은 더 구체적인 code를 정의한다.

## 14. Source authority matrix 필수 열

모든 후속 구현 문서는 다음 표를 포함한다.

| meaning | authoritative source | physical writer | semantic owner | consumer | version owner | migration gate |
|---|---|---|---|---|---|---|

shadow, cache, index, derived inference는 authority 여부를 명시해야 한다.
