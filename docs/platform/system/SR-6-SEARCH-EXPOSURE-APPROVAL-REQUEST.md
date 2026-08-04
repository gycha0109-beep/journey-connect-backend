# SR-6 Search Exposure System Coordination Approval Request

## 요청 상태

```text
Request: OPEN
Contract: search_exposure_v1
Implementation permission: NOT_GRANTED
Database sequence: NOT_ASSIGNED
Runtime activation: NOT_ALLOWED
```

## 배경

SR-3은 signed Search result context와 `CLICK`·`VIEW` behavior fact를 구현했다. SR-5는 frontend 연결 패키지를 준비했지만 authoritative `IMPRESSION`은 전송하지 않는다.

`search_exposure_v1`은 registry ID만 예약되어 있다. 실제 persistence를 시작하려면 System Contract가 요구하는 physical writer, identity, retention/privacy, visibility rule, metric authority, canonical DB sequence를 먼저 결정해야 한다.

## 승인 요청 항목

| 번호 | 결정 항목 | 권장안 | 승인 전 상태 |
|---:|---|---|---|
| 1 | semantic owner | Intelligence Platform / Search | 제안 |
| 2 | physical writer | Search runtime의 단일 `SearchExposureStore` | 제안 |
| 3 | 초기 DB role | recommendation backend role compatibility arrangement | 승인 필요 |
| 4 | identity scheme | `platform_subject_v1` | 승인 필요 |
| 5 | identity mapping owner | System Coordination 지정 단일 owner | 미지정 |
| 6 | anonymous exposure | V1 미지원 | 제안 |
| 7 | visibility rule | `search-item-visible-v1`: 50% / 1,000ms | Operations·Reliability 승인 필요 |
| 8 | raw retention | 기술 기본 180일 | Data·privacy 승인 필요 |
| 9 | deletion 방식 | mapping invalidation 우선, 법적 삭제 방식 별도 결정 | 미결정 |
| 10 | metric | `search-click-through-rate-v1`, 30분 attribution | Reliability 승인 필요 |
| 11 | target DB version | System Coordination 지정 | 미배정 |
| 12 | SQL sequence | canonical `26` 이후 next available | 미배정 |

## 권장 승인 문구

```text
SC-1 approves search_exposure_v1 for implementation preparation only.

Semantic owner: Intelligence Platform / Search.
Physical writer: one Search runtime application boundary.
Anonymous exposure: disabled for V1.
Identity: platform_subject_v1 through an approved IdentityMappingReadPort.
Visibility candidate: search-item-visible-v1, subject to Operations/Reliability sign-off.
Metric candidate: search-click-through-rate-v1, subject to Reliability sign-off.
Database DDL remains prohibited until target DB version, SQL sequence, role/grant,
retention and deletion handling are separately approved.
```

이 승인 수준은 Java contract type·validator·canonicalizer와 fixture를 작성할 수 있게 하지만 production DDL 또는 runtime write를 허용하지 않는다.

## Compatibility arrangement 요청

초기 구현이 기존 backend process 안에서 동작하므로 dedicated Intelligence DB role을 즉시 만들지 않고 현재 recommendation backend role을 사용하는 안을 제안한다.

조건:

- writer class와 repository를 Search package로 제한
- 다른 recommendation exposure table write 금지
- table grant를 application role 하나에만 부여
- Data/Reliability role은 read projection만 사용
- dedicated role 분리는 별도 High-risk migration으로 처리

System Coordination이 이 arrangement를 거부하면 SR-6C 이전에 dedicated role과 secret/provisioning 계획이 필요하다.

## Identity 결정 요청

권장안은 `platform_subject_v1`이다. Search runtime은 numeric user ID를 exposure row에 직접 저장하지 않고 승인된 mapping port에서 opaque subject를 읽는다.

필요 결정:

- mapping physical owner
- Search read purpose allowlist
- mapping unavailable 시 error code
- mapping audit retention
- account deletion/invalidation 처리
- historical exposure의 unlinkability 보장 방식

위 항목이 결정되지 않으면 exposure runtime write는 시작하지 않는다.

## Visibility·metric 결정 요청

### Visibility candidate

```text
rule: search-item-visible-v1
viewport ratio: >= 50%
continuous dwell: >= 1,000ms
document visibility: visible
unit: item occurrence
```

### Metric candidate

```text
metric: search-click-through-rate-v1
denominator: deduped eligible search_exposure_v1 item occurrences
numerator: bound CLICK behavior fact
attribution: exposedAt < clickAt <= exposedAt + 30 minutes
```

`VIEW`는 CTR에 합산하지 않는다. 필요 시 별도 metric version을 만든다. Search exposure를 일반 추천 또는 P2 metric denominator에 합산하지 않는다.

## DB sequence 요청

현재 canonical baseline은 `journey-connect-db-v2.7/01..26`이다.

요청:

```text
target DB version: SC assignment required
SQL sequence: next available after 26
DDL owner: Intelligence Search, reviewed by Data/System Coordination
role/grant: explicit application writer + read-only projection consumers
```

번호 배정 전 executable SQL, Flyway migration, JPA entity를 작성하지 않는다.

## 승인 후 작업

### Partial approval

다음만 가능:

- Java request/domain type
- validator
- canonical payload/fingerprint
- deterministic fixture
- contract test
- no-op persistence port

### Full DB approval

다음 추가 가능:

- canonical DDL
- role/grant
- `SearchExposureStore`
- API integration
- PostgreSQL idempotency tests
- retention/deletion fixture

### Production activation approval

다음 증거가 필요하다.

- frontend visibility implementation
- browser verification
- full backend P0/P1/P2 regression
- PostgreSQL role/grant smoke
- replay/golden canonical payload
- privacy·retention sign-off
- Reliability metric validation
- independent review

## 명시적 비승인 항목

이 요청은 다음을 승인하지 않는다.

- merge 또는 배포
- Search `IMPRESSION` 즉시 전송
- recommendation exposure table 재사용
- P2 experiment denominator 변경
- anonymous actor ID 임의 생성
- raw query·JWT 저장
- SQL sequence 자체 선택
