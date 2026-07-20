# Search Domain Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-domain-contract-v1` |
| 상태 | `ACTIVE DESIGN / PRODUCTION NOT STARTED` |
| 소유 | Intelligence Platform / Search Intelligence |
| 공통 계약 | IP-1 `intelligence-*-v1` |

## 2. 책임

Search Intelligence가 소유한다.

- query normalization 의미
- search intent/context representation
- retrieval request와 strategy selection
- candidate retrieval evidence
- query/user/system filter 적용 순서
- Operations eligibility consumption
- relevance ranking/reranking
- deterministic final ordering
- snapshot-bound pagination/cursor
- result/output/explanation evidence
- search fallback/failure/no-result reason
- SearchRun과 search exposure 의미

## 3. 비책임

| 영역 | owner |
|---|---|
| raw behavior ingestion/canonical event | Data |
| visibility/moderation/eligibility decision | Operations |
| experiment metric/release gate | Reliability |
| contract registry/breaking change | System Coordination |
| recommendation score/diversity/exploration | Recommendation Intelligence |
| place fact authority/freshness | 지정 provider/Data/System Coordination |
| 사용자 원문 콘텐츠 수정 | Content domain owner |

## 4. Search aggregate

### 4.1 `SearchRequestV1`

논리 구성:

- `requestId`
- `correlationId`
- `query`
- `context`
- `filters`
- `sort`
- `pageRequest`
- `surface`
- `entityScope`
- `referenceTime`
- `schemaVersion`
- `queryNormalizationVersion`
- `rankingPolicyVersion`
- `featureDefinitionVersion`

client는 subject, role, account status, canonical session binding, Operations eligibility를 결정하지 않는다.

### 4.2 `SearchRunV1`

Search domain terminal evidence:

- `runId`
- `requestId`, `correlationId`
- `subjectRef`/identity scheme if applicable
- `sessionRef`
- `surface`, `entityScope`
- `inputSnapshotRef`
- `candidateSnapshotRef`
- `outputSnapshotRef`
- `queryNormalizationVersion`
- `retrievalStrategyVersion`
- `rankingPolicyVersion`
- `featureDefinitionVersion`
- `referenceTime`
- `producerBuildId`
- `status`
- `fallbackCode` or `failureCode`
- `replayClass`

V1은 queued/running row가 아니라 terminal evidence다.

## 5. Common contract mapping

### 5.1 SearchRun → `IntelligenceRunV1`

| Search 의미 | common mapping |
|---|---|
| run ID | `runId` |
| run type | `search` |
| terminal status | `succeeded`, `fallback`, `failed` |
| request/correlation | same field |
| subject | same field, scheme 보존 |
| input | `inputSnapshotRef` |
| output | `outputSnapshotRef` |
| ranking policy | `policyVersion` |
| ranking feature definition | `featureDefinitionVersion` |
| build/time/replay | same field |
| search surface | `surface` |
| search mode | `domainRunMode` |
| exposure source | `search_exposure_v1`, 실제 exposure 결속 시만 |

현재 IP-1 `IntelligenceRunV1`에는 `candidateSnapshotRef`, `queryNormalizationVersion`, `retrievalStrategyVersion` 전용 필드가 없다. 기존 common contract를 변경하지 않는다.

SearchRun은 해당 값을 Search domain record에 보존하고, `IntelligenceOutputSnapshotV1.domainExtensionRef`가 가리키는 `search-output-extension-v1`에서 candidate snapshot과 search version vector를 연결한다. IP-3는 이 논리 타입을 구현하되 IP-1 common record를 수정하지 않는다.

### 5.2 Snapshot mapping

| Search contract | common contract | domain extension |
|---|---|---|
| `SearchInputSnapshotV1` | `IntelligenceInputSnapshotV1` | original/normalized query refs, filter/sort/context/version |
| `SearchCandidateSnapshotV1` | `IntelligenceCandidateSnapshotV1` | retrieval source/score/source rank/eligibility/metadata |
| `SearchOutputSnapshotV1` | `IntelligenceOutputSnapshotV1` | final rank/score semantics/candidate snapshot/query fingerprint |

Recommendation candidate score와 Search relevance score를 같은 scale로 해석하지 않는다.

## 6. Entity scope와 surface

### 6.1 `SearchEntityScope`

```text
post
region
tag
place
user
crew
all
```

`all`은 여러 scope contract를 조합하는 orchestration 의미이며 하나의 테이블이나 동일 score scale을 의미하지 않는다.

### 6.2 `SearchSurface`

```text
global_search
explore
region_search
tag_search
place_search
user_search
crew_search
```

새 wire value는 version/registry 검토 없이 문자열 추측으로 처리하지 않는다.

## 7. 상태

### `succeeded`

정상 output을 생성했다. 결과 0건도 가능하다.

### `fallback`

primary path 실패 후 bounded fallback path가 output을 생성했다. `fallbackCode` 필수다.

### `failed`

유효 output을 생성하지 못했다. `failureCode` 필수다.

partial 결과가 필요하면 기존 `IntelligenceRunStatus`를 조용히 확장하지 않고 새 contract version 또는 domain output completeness field를 제안한다.

## 8. Current implementation compatibility

| current component | classification |
|---|---|
| `/api/v1/explore` | future read-adapter candidate |
| `JourneyPostRepository.explore` | intentionally legacy/domain-specific query |
| `/api/v1/regions?keyword` | future region retrieval adapter candidate |
| feed `CursorCodec` | intentionally feed-specific, Search cursor로 재사용 금지 |
| recommendation behavior `search` enum | behavior vocabulary only, SearchRun/exposure 아님 |
| `search_exposure_v1` | reserved source ID, persistence absent |

## 9. Invariants

1. run/snapshot/output은 append-only evidence다.
2. original query와 normalized query를 분리한다.
3. source rank와 final rank를 분리한다.
4. rank는 1-based다.
5. output ordering은 complete deterministic tuple을 가진다.
6. visibility authority를 Search가 생성하지 않는다.
7. Search exposure와 recommendation exposure를 합산하지 않는다.
8. unknown required enum은 fail-closed다.
9. `latest`, `current`, `default`를 영속 version으로 사용하지 않는다.
