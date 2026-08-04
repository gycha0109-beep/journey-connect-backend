# SR-5 Search Frontend Integration Handoff

## 상태

```text
Implementation: COMPLETE AS HANDOFF PACKAGE V2
Static parse: 5/5 PASS
Service contract tests: PASS
Pagination race regression: PASS
Frontend remote write: BLOCKED_BY_GITHUB_APP_PERMISSION
Frontend lint/build CI: NOT_RUN_ENVIRONMENT_BLOCKED
Authoritative search exposure: OUT_OF_SCOPE
Overall: IMPLEMENTED_UNVERIFIED
```

## 대상

```text
repository: YTAK99/Journey-Connect
branch: jihwan/recommendation-algorithm-integration
expected head: e7a10a31e70b4801cf2a1268a1c15f8e9e3478e1
open PR: #8
```

대상 저장소는 읽기 가능하지만 다음 쓰기 요청이 모두 GitHub에서 `403 Resource not accessible by integration`으로 거부되었다.

- branch 생성
- ref 갱신
- Contents API 파일 생성
- Contents API 파일 수정

로컬 컨테이너의 일반 GitHub clone과 npm registry dependency fetch도 차단되어 전체 프론트 빌드를 실행할 수 없었다. 따라서 SR-5 구현은 blob SHA 검증형 PowerShell 적용 스크립트, 실제 교체 파일, manifest, 검증 기록을 포함한 v2 handoff ZIP으로 제공한다.

## 구현 범위

- 기존 `getExplore` 본문 반환 계약 유지
- `getExploreWithContext` 추가
- `X-Search-Snapshot`을 다음 페이지 요청에 전달
- `X-Search-Result-Context`, `X-Search-Run-Id`, `X-Search-Policy-Version` 보존
- 선택 지역을 backend `region` 파라미터로 전달
- 18개 단위 서버 pagination과 더 보기 UI
- 검색 조건 변경 시 이전 snapshot/context/pending pagination 상태 초기화
- `SEARCH_SNAPSHOT_EXPIRED` 발생 시 첫 페이지 재조회
- 검색 결과 카드 `CLICK` 행동 기록
- 검색 상세 진입 `VIEW` 행동 기록
- search occurrence별 결정론적 event ID/idempotency key 사용
- 인증 토큰과 유효한 result context가 있을 때만 `/api/v1/search/events` 호출
- 일반 피드, 빈 결과 fallback 추천, 직접 상세 접근은 검색 행동으로 기록하지 않음

## v2 검수 보완

정적 검수에서 다음 race condition을 발견했다.

```text
이전 더 보기 요청 진행
→ 검색어 또는 지역 변경
→ 새 첫 페이지 요청 시작
→ 이전 요청 finally는 request key 불일치로 loadingMore를 해제하지 않음
→ 새 검색 화면의 더 보기 버튼이 계속 비활성화될 가능성
```

첫 페이지 요청 시작 시 `setLoadingMore(false)`를 수행하도록 수정했다. 검색 조건 변경 시 이전 snapshot/context뿐 아니라 pending pagination UI 상태도 함께 폐기한다.

## 변경 파일

```text
jc-frontend/src/services/postApi.js
jc-frontend/src/components/PostCard.jsx
jc-frontend/src/pages/SearchPage.jsx
jc-frontend/src/pages/TrackedPostDetail.jsx
jc-frontend/src/App.jsx
```

기존 파일 blob 전제:

```text
postApi.js     da4c84b49c0b1af95c0bbd185d43b6dfcbddac6c
PostCard.jsx   47e9170d8efebb82835cd7524b6c9b177c3741d8
SearchPage.jsx de5943583384aa90e89dccc534909e39ba8ca580
App.jsx        297392c4a7c2543c81c61951998a7dfc6c9cfddc
```

## 제외 범위

- `IMPRESSION` 전송
- `search_exposure_v1` authoritative persistence
- visibility threshold/dwell rule 결정
- frontend 저장소 merge 및 배포
- backend SR-3/SR-4 의미 변경

## 직접 검증

```text
TypeScript transpile parser: 5/5 PASS
postApi context/event contract tests: PASS
SearchPage helper tests: PASS
pending load-more request reset invariant: PASS
replacement SHA-256 manifest: PASS
PowerShell embedded payload equality: PASS
```

미완료:

```text
npm ci
npm run lint
npm run build
browser integration
GitHub Actions Frontend CI
```

프론트 원격 권한과 dependency fetch가 가능한 환경에서 v2 패키지를 적용하고 위 미완료 검증을 수행해야 `VERIFIED`로 전환할 수 있다.
