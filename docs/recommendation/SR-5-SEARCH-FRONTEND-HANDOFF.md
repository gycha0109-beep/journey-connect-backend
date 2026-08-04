# SR-5 Search Frontend Integration Handoff

## 상태

```text
Implementation: COMPLETE AS HANDOFF PACKAGE
Frontend remote write: BLOCKED_BY_GITHUB_APP_PERMISSION
Frontend lint/build CI: NOT_RUN
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

따라서 SR-5 구현은 blob SHA 검증형 PowerShell 적용 스크립트와 완성된 교체 파일 묶음으로 생성했다. 원격 저장소에는 직접 반영하지 못했다.

## 구현 범위

- 기존 `getExplore` 본문 반환 계약 유지
- `getExploreWithContext` 추가
- `X-Search-Snapshot`을 다음 페이지 요청에 전달
- `X-Search-Result-Context`, `X-Search-Run-Id`, `X-Search-Policy-Version` 보존
- 선택 지역을 backend `region` 파라미터로 전달
- 18개 단위 서버 pagination과 더 보기 UI
- 검색 조건 변경 시 이전 snapshot/context 폐기
- `SEARCH_SNAPSHOT_EXPIRED` 발생 시 첫 페이지 재조회
- 검색 결과 카드 `CLICK` 행동 기록
- 검색 상세 진입 `VIEW` 행동 기록
- search occurrence별 결정론적 event ID/idempotency key 사용
- 인증 토큰과 유효한 result context가 있을 때만 `/api/v1/search/events` 호출
- 일반 피드, 빈 결과 fallback 추천, 직접 상세 접근은 검색 행동으로 기록하지 않음

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
postApi.js    da4c84b49c0b1af95c0bbd185d43b6dfcbddac6c
PostCard.jsx  47e9170d8efebb82835cd7524b6c9b177c3741d8
SearchPage.jsx de5943583384aa90e89dccc534909e39ba8ca580
App.jsx       297392c4a7c2543c81c61951998a7dfc6c9cfddc
```

## 제외 범위

- `IMPRESSION` 전송
- `search_exposure_v1` authoritative persistence
- visibility threshold/dwell rule 결정
- frontend 저장소 merge 및 배포
- backend SR-3/SR-4 의미 변경

## 검증 상태

완료:

- JavaScript service 파일 `node --check`
- replacement 파일 괄호/블록 균형 검사
- 적용 스크립트 내 5개 embedded payload와 교체 파일 byte equality 검사
- handoff ZIP SHA-256 생성

미완료:

- `npm ci`
- `npm run lint`
- `npm run build`
- 브라우저 수동 검증
- GitHub Actions Frontend CI

원격 쓰기 권한이 복구된 뒤 적용하고 위 검증을 수행해야 `VERIFIED`로 전환할 수 있다.
