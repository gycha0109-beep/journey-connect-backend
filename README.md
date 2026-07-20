# 🗺️ Journey Connect (서비스명: **여정**)

"여정"을 강조처리
> **"여행정보는 여정에서"**
> **"누군가의 여행이, 당신의 여정이 되다."**
> **"지역별 여행정보를 한눈에"**

---

## 🎯 기획 배경 및 차별점 (Service Identity)

* **기존 서비스의 한계:** 맛집, 명소 등의 정보 검색이나 AI 추천은 단편적이고 파편화되어 있음. 트립닷컴 같은 티켓팅 사이트가 정보를 일부 제공하지만, 유저가 원하는 '지역별 동선 중심'으로 보기 좋게 정리된 플랫폼이 부재함.
* **프로젝트 핵심 가치:** 대부분의 유사 서비스는 정적이고 폐쇄적임. JC(여정)는 글로벌 사용자들이 자신의 실제 이동 동선과 찐 현지 정보를 **실시간으로 공유하고 소통**하는 오픈 플랫폼을 지향함.
* **차별화 요소:** 딱딱한 기존 플래너 형식을 탈피하고, 실시간성 기반의 로컬 연결(Crew) 및 인터랙티브 지도를 통한 직관적인 UI 제공.

---

## 🛠️ 개발 환경 및 기술 스택 (Tech Stack)

### 💻 Frontend

* **Core:** React, React Router
* **HTTP Client:** Axios (API 통신)
* **Styling:** Tailwind CSS + Shadcn UI (또는 Tailwind CSS + Headless UI)
* **State Management:** Zustand (전역 상태 관리)

### ☕ Backend

* **Core:** Spring Boot 3.x (Java 17 이상)
* **Security:** Spring Security + JWT (인증/인가)
* **Data Access:** Spring Data JPA + QueryDSL
* *기본 CRUD는 JPA로 생산성 확보, 복잡한 주변 장소 조회(공간 연산) 쿼리는 QueryDSL 조합*


* **Utility:** Validation, Lombok

### 🗄️ Database & Infrastructure

* **DB:** PostgreSQL 16+ (with **PostGIS 3+** 위치 정보 확장 모듈 필수)
* **Storage:** AWS S3 (이미지 및 파일 업로드)

### 🧰 Management Tools

* **DB Client:** DBeaver
* **API Test:** Postman
* **VCS:** GitHub
* **Cloud:** AWS

---

## 🎨 UI/UX 스타일 가이드 후보 (Color Palette Options)

*팀원들과 UI 디자인 시스템 결정 시 아래 4가지 후보 중 선택하여 반영할 예정입니다.*

### [스타일 1] Deep Teal (차분하고 신뢰감 있는 무드)

* **Primary:** `#1D5C63` / **Hover:** `#2A7078` / **Light:** `#EAF7F8` / **Background:** `#F8FCFC` / **Text:** `#23303B`

### [스타일 2] Ocean Navy (시원하고 깔끔한 여행 가이드 느낌)

* **Primary:** `#0F4C81` / **Accent:** `#00B8D9` / **Light:** `#E9F7FB` / **Background:** `#F7FBFD`

### [스타일 3] Aqua & Coral (활기차고 톡톡 튀는 소셜 커뮤니티 느낌)

* **Aqua Blue:** `#00D2D3` / **Sky Gray:** `#F0F8FF` / **Deep Teal:** `#004753` / **Coral:** `#FF7F50`

### [스타일 4] Ocean Muted (감성적이고 편안한 톤온톤 느낌)

* **Background:** `#F2F9FA` / **Primary:** `#1A8090` / **Accent:** `#2596A8` / **Secondary:** `#DCF0F3` / **Muted text:** `#5E8890`

---

## 📋 페이지별 상세 기능 명세서 (Functional Specifications)

### 1. 메인 페이지 (Main / Home)

* **인터랙티브 지도:** 시각적인 지도 UI를 전면에 배치하여 클릭 시 지역별 피드로 직관적인 이동 지원.
* **실시간 트렌딩 태그:** AI 추출 및 실시간 인기 검색 태그(#지역, #테마 등)를 롤링 컴포넌트로 노출.
* **인기 여정 슬라이더:** 좋아요와 조회수 데이터를 결합한 주간 인기 게시글 슬라이더 (페이징/네비게이션 탑재).
* **통합 검색창:** 화면 중앙에 배치하여 지역, 맛집, 명소, 태그를 한 번에 검색 가능하도록 구현.

### ✍️ 2. 여정 작성 페이지 (Create Post)

* **구글 맵스 플레이스 API 연동:** 장소 명칭 검색 시 정확한 주소와 좌표를 가져오고, 카테고리(식당, 카페 등) 자동 인식.
* **자동 카테고리 태깅:** 장소 카테고리에 맞춰 `#식당`, `#명소` 등의 1차 태그 자동 생성 및 부여.
* **드래그 앤 드롭 동선 편집:** 직관적인 UI 인터랙션(Drag & Drop)을 통해 여행 장소들의 방문 순서(일정)를 자유롭게 변경.
* **미디어 업로드:** 여행지 관련 이미지 및 파일 업로드 기능 (AWS S3 스토리지 연동).

### 📱 3. 여정 피드 및 상세 페이지 (Feed & Detail)

* **구글 지도 마커 & Polyline 시각화:** 게시글에 등록된 장소들을 지도 위에 마커로 표시하고, 방문 순서대로 선(Polyline)을 그려 동선을 한눈에 파악하도록 구현.
* **AI 본문 요약:** 긴 여행 후기 글을 AI API로 처리하여 가독성 좋은 핵심 3줄 내외 요약문으로 상단 배치.
* **AI 스타일 태그 생성:** 작성자가 쓴 텍스트 분위기를 분석하여 감성 태그(#가성비, #뷰맛집, #힐링 등) 자동 등록.
* **소통 시스템:** 익명 기반의 계층형 댓글 및 대댓글 구조 구현.
* **상호작용:** 마음에 드는 게시글을 보관하는 좋아요 및 스크랩 기능 (인기도 점수 산정에 반영).
* **주변 여정 추천:** 상세 페이지 내 등록된 중심 좌표 기준, **반경 5km 이내**의 다른 장소 카드를 큐레이션하여 노출.
* **평점 시스템:** 사용자 경험 기반의 세분화된 별점(1점 ~ 10점) 부여 기능.

### 👥 4. 마이페이지 (My Page)

* **위트 있는 랜덤 닉네임 생성기:** 가입 시 혹은 정보 변경 시 자동으로 위트 있는 익명 조합형 닉네임(예: *"미련한 토끼"*)을 부여하여 활동 부담 완화.
* **나만의 여정 맵:** 본인이 작성한 글이나 좋아요/스크랩을 누른 장소 데이터만 모아서 개인 지도 위에 마커로 누적 시각화.
* **활동 내역 관리:** 내가 쓴 글 및 댓글 목록을 페이징 처리하여 한눈에 모아보기.

### ➕ 5. [추가 제안 기능] 크루(Crew) / 소모임 피드

* **목적:** 특정 루트 및 일정을 기반으로 한 외국인과 현지인 매칭 중심의 소셜 피드.
* **예시:** "7월 서울 성수동 투어할 사람 구합니다", "도쿄 빈티지샵 투어 같이 동행하실 분?" 등 목적성 기반의 실시간 구인 및 네트워킹 유도.

---

## 🔒 공통 시스템 및 인프라 설계 (Infrastructure & Common)

### 1. 보안 및 데이터 통신

* **인증 시스템:** Spring Security + JWT 토큰 기반의 로그인/회원가입 인프라 구축.
* **REST API 아키텍처:** React와 Spring Boot 간의 데이터 송수신을 명확하게 분리하고 확장성을 고려해 URI에 버전 명시 필수 (`/api/v1/...`).
* **글로벌 페이징:** 대량의 데이터(게시글, 댓글) 호출 시 서버 부하 및 프론트 렌더링 최적화를 위해 무한 스크롤 또는 페이지네이션 공통 처리.
* **체크리스트 기능:** 여행 전 필수 준비물을 관리하고 확인할 수 있는 유저별 토글 리스트 기능.

### 🌐 2. 다국어 지원 설계 (i18n Implementation)

* **Frontend (UI 번역):**
* 사용 도구: `react-i18next` 라이브러리 활용.
* 작동 원리: `ko.json`, `en.json` 번역 데이터셋을 세팅해두고 사용자가 언어 변경 시 리액트 상태에 따라 즉시 뷰 갈아 끼움.


* **Backend (시스템 메시지 번역):**
* 사용 도구: Spring Boot 기본 기능인 `MessageSource` 활용.
* 작동 원리: `messages_en.properties`, `messages_ko.properties`를 구축하여 유저 헤더의 언어 설정(Locale)에 매칭되는 에러 및 알림 문구를 동적 리턴.


* **Database (동적 데이터 번역):**
* 작동 원리: 작성자가 쓴 원문 데이터는 그대로 보존하고, 서비스 내에서 타국어 사용자가 **'번역 보기'** 버튼을 누르면 외부 번역 API(Google Translate 등)를 호출하여 실시간으로 결과 전송.



---

## ⚠️ 팀 공통 개발 규칙 (Development Rules)

### 1. 환경 변수 및 보안 관리 (Security)

* Google Maps API Key, AI API Key, DB 패스워드 등 모든 민감 정보는 외부 유출 방지를 위해 `.env` (Front) 및 `application.yml` (Back) 시스템 환경 변수로 로드.
* 해당 설정 파일들은 반드시 `.gitignore`에 등록하여 GitHub 원격 저장소 업로드 대상에서 제외할 것.

### 2. 코드 명명 규칙 (Naming Conventions)

* **Frontend:**
* 컴포넌트 파일: 파스칼 케이스 (PascalCase) ➡️ `CreatePost.jsx`
* 변수 및 함수: 카멜 케이스 (camelCase) ➡️ `handleSearchLocation`


* **Backend:**
* 클래스명: 파스칼 케이스 (PascalCase) ➡️ `PostController`
* 변수 및 메서드명: 카멜 케이스 (camelCase) ➡️ `getNearbyJourneys`


* **Database:**
* 테이블 및 컬럼명: 스네이크 케이스 (snake_case) ➡️ `journey_post`



### 3. API 설계 표준

* URL은 자원(Resource) 중심으로 명사형 다수 형태를 가집니다.
* `GET /api/v1/journeys` (전체 여정 조회)
* `POST /api/v1/journeys/{id}/comments` (댓글 등록)


* **Swagger / Springdoc**을 연동하여 API 개발과 동시에 명세서가 자동으로 최신화되도록 세팅.

### 4. 세팅 및 포맷 고려 사항

* **CORS 설정:** 프론트(React)와 백엔드(Spring) 포트 간 통신 허용 설정 필수.
* **데이터 포맷:** 모든 날짜 및 시간 포맷은 `YYYY-MM-DD HH:mm:ss`로 통일.
* **디자인 세팅:** favicon 로고 지정, 전역 상태(Zustand) 설계, 테마 관리를 위한 **다크 모드** 고려.
* **코드 주석 필수:** 복잡한 도메인 로직인 **AI 요약 프롬프트 전달 프로세스**, **PostGIS를 활용한 반경 5km 이내 거리 계산 및 반환 로직** 코드에는 기능 요약뿐만 아니라 '왜 이렇게 설계하고 구현했는지(Why)'에 대한 상세 주석을 무조건 기재할 것.

---
---

## 🧠 추천 알고리즘 P1 상태

- Java recommendation core `1.1.0`
- 행동 기반 profile snapshot 및 명시적 cold-start 선호
- 세그먼트·surface별 versioned policy selector
- popularity bias·low-exposure·diversity 보정
- P0 baseline과 분리된 SHADOW/CANARY treatment evidence
- PostgreSQL DB v2.6 (`01~24`)
- 최종 상태: **P1 CLOSED**
- 상세: `P1_IMPLEMENTATION_INDEX.md`

---

## 📊 추천 알고리즘 P2 상태

- Java recommendation core `1.2.0` P2 통계 평가 계층
- 결정론적 baseline/treatment 실험 배정 및 실제 노출 결속
- versioned metric definition과 canonical dataset snapshot
- bootstrap confidence interval, effect size, Holm 보정, segment comparison
- Gate A~E 분리 및 append-only release/rollback evidence
- PostgreSQL DB v2.7 (`01~26`)
- 기술 구현 상태: **P2 CLOSED**
- 운영 출시 상태: **HOLD — 실제 CANARY 표본 및 운영 승인 대기**
- 상세: `P2_IMPLEMENTATION_INDEX.md`
