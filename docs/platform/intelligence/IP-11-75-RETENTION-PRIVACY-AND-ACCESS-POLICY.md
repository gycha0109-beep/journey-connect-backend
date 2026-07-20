# IP-11.75 Retention, Privacy and Access Policy

## 승인 상태

`POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING`

- Raw query, raw/normalized query 원문, raw identity, JWT subject, session ID, full request/response payload: `PROHIBITED`
- Aggregate/bucket evidence: 최대 `14일`; persistence 구현 시 생성 시각 기반 자동 삭제 필수
- Error summary: 최대 `30일`; enum category, bucket, version만 허용
- 접근: `Project Owner`, `Backend Owner`
- 개인정보·raw data·오기록·정책 위반·잘못된 결속 발견 시 즉시 삭제
- 현재 production sink는 no-op이고 persistent storage/cleanup job은 없음
- 따라서 `POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING`; production persistence는 계속 `DISABLED`

## Aggregate evidence lifecycle

| 단계 | 계약 |
|---|---|
| 생성 | privacy-safe aggregate/bucket only |
| 최대 보관 | 14일 |
| 자동 삭제 | `created_at + 14 days` 또는 동등 TTL |
| 접근 | Project Owner, Backend Owner |
| 즉시 삭제 | 개인정보/raw data/오기록/잘못된 결속/정책 위반 |

## Error summary lifecycle

| 단계 | 계약 |
|---|---|
| 허용 | enum error category, latency bucket, component category, opaque correlation, schema/policy version |
| 금지 | raw exception message의 query, payload, identity, candidate/post list |
| 최대 보관 | 30일 |
| 자동 삭제 | `created_at + 30 days` 또는 동등 TTL |

## 현재 구현 경계

production evidence sink는 no-op이며 DB/filesystem/network writer와 cleanup scheduler가 없다. 따라서 자동 삭제가 현재 실행된다고 주장하지 않는다. 향후 persistence 단계는 TTL, ACL, deletion audit, immediate deletion path, raw-data prohibition과 integration test를 필수로 한다.
