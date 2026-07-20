# P1-3 Quality, Retrieval & Diversity

## 목적

행동 프로필을 사용해 관심 적합도를 반영하면서 popularity 편향을 압축하고 low-exposure 후보를 보정하며, surface별 다양성 제약 아래 결정론적 최종 순위를 생성한다.

## 주요 변경 파일

- `jc-recommendation-core/.../p1/ranking/*`
- `jc-recommendation-core/.../p1/evaluation/*`
- `jc-backend/.../recommendation/p1/RecommendationP1CandidateSource.java`
- `jc-backend/.../recommendation/p1/RecommendationP1CandidateMapper.java`
- DB v2.6 P1 tag seed 및 canonical SQL `23~24`

## 구현

- eligibility query → retrieval pool → pre-rank/core 최대 100개
- 후보별 region/author/theme/activity feature mapping
- interest/context/freshness/popularity/low-exposure component 분리
- 로그 기반 popularity compression과 bounded low-exposure boost
- 완전한 tie-break와 입력 순서 독립성
- author/region/theme cap, 단계적 relaxation, 승격·강등 폭 제한
- baseline/treatment overlap, rank displacement, 다양성, low-exposure share, popularity 지표
- ranking 결과에 component provenance와 정책 버전 저장

## 검증 및 보완

- ordering determinism, candidate limit, duplicate identity, prefer/avoid PASS
- popularity 압축과 low-exposure 보정 PASS
- diversity cap·relaxation·movement bound PASS
- PostgreSQL 후보 조회 및 최근 노출 집계 PASS
- PostgreSQL timestamp 타입 추론 오류를 명시적 `TIMESTAMPTZ` cast로 보완
- diversity movement bound가 일부 경계에서 깨지던 알고리즘 보완
- diversity metadata가 fingerprint에서 누락되던 문제 보완
- 저장용 snapshot ID가 결과 fingerprint를 오염시키지 않도록 계산 경계 분리

## 잔여 리스크

- feature vocabulary는 현재 지원 가능한 지역·태그 집합으로 제한된다. 확장 시 새 vocabulary version과 회귀 fixture가 필요하다.
