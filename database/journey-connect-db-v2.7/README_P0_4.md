# P0-4 SHADOW Orchestration

- `17`: exploration 삽입을 반영해 recommendation run의 입력 분할 제약을 `final-ranked + terminal`로 수정합니다.
- `18`: PostgreSQL 메타데이터에서 두 개수 제약을 검증하고 롤백합니다.
- 기존 `01~16`은 v2.2와 exact match를 유지합니다.
- 백엔드는 기본 `OFF`, 인증 사용자의 `/feed` 첫 페이지에서만 `SHADOW` 계산·저장하며 기존 응답은 변경하지 않습니다.
- `CANARY`, `LIVE`는 아직 시작 단계에서 차단됩니다.
