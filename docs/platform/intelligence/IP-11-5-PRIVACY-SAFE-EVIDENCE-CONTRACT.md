# IP-11.5 Privacy-safe Evidence Contract

## IP-11.75 governance amendment

- Raw query, raw/normalized query 원문, raw identity, JWT subject, session ID, full request/response payload: `PROHIBITED`
- Aggregate/bucket evidence: 최대 `14일`; persistence 구현 시 생성 시각 기반 자동 삭제 필수
- Error summary: 최대 `30일`; enum category, bucket, version만 허용
- 접근: `Project Owner`, `Backend Owner`
- 개인정보·raw data·오기록·정책 위반·잘못된 결속 발견 시 즉시 삭제
- 현재 production sink는 no-op이고 persistent storage/cleanup job은 없음
- 따라서 `POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING`; production persistence는 계속 `DISABLED`

Allowed evidence remains timestamp, opaque run ID, version/status enums and bounded latency/candidate/overlap/divergence/freshness buckets. Production sink remains no-op; no retention writer or cleanup scheduler is implemented.
