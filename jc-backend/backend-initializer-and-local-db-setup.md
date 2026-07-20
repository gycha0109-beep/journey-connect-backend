# 스프링이니셜라이저 설정
- Project: Gradle - Kotlin
- Language: Java 21
- Spring Boot: 3.5.16
- Group: com.jc
- Artifact: jc-backend
- Package name: com.jc.backend
- Dependencies:
Spring Web
Spring Data JPA
Spring Security
Validation
Lombok
PostgreSQL Driver

# application.yml 파일 설정
- src/main/resources/application.yml.sample 코드에서 비밀번호 부분 수정 후
- application.yml 파일 생성해서 복붙.

# PostgreSQL 18.4-2 설치
https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
- 설치할때 PostGIS 선택해서 설치 진행.
- username은 postgres 그대로 두고 비번은 맘대로.
- 설치 후에 DBeaver에서 새연결로 활성화 시켜주기.
- 그리고 새 SQL편집기에 아래 코드 입력 후 실행.

```sql
-- 1. PostGIS 공간 데이터 확장 팩을 현재 데이터베이스(journey_db)에 설치 및 활성화합니다.
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. 설치가 잘 되었는지 전체 버전 및 연동 상태를 콘솔창에서 조회합니다.
SELECT PostGIS_Full_Version();

-- 3. (확인용) 공간 데이터 좌표계 시스템(Spatial Ref) 테이블이 정상적으로 연동되어 작동하는지 검증합니다.
SELECT * FROM spatial_ref_sys LIMIT 5;

create database journey_db;
```

- 이후 JcBackendApplication 실행으로 잘 되는지 확인.
- 프로젝트 인식 안될경우 Gradle - jc-backend 우클릭 - Gradle 프로젝트 동기화