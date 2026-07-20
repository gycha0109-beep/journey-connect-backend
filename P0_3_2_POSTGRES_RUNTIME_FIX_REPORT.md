# P0-3.2 PostgreSQL runtime regression fix

## 변경

- DB v2.2 증분 SQL `15~16` 추가
- crew deferred trigger 내부 함수를 `SECURITY DEFINER` + 고정 `search_path`로 보강
- `jc_security_owner`에 crew 무결성 검사용 읽기 권한만 부여
- 역할 경계를 왜곡하던 테스트 외부 `@Transactional` 제거
- 게시글 공개범위 테스트에 검증된 요청 사용자 컨텍스트 적용
- 초안 테스트 데이터는 `PostStatus.DRAFT`로 명시 생성

## 검증

- PostgreSQL 15.18 직접 기동: PASS
- canonical SQL `01~16`: PASS
- Java `p0Verification`: PASS
- 백엔드 테스트: 42/42 PASS
- 추천 Core `check`: PASS

## 판정

P0-3 역할 라우팅의 PostgreSQL 런타임 회귀를 해소했습니다.
