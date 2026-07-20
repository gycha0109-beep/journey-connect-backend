# jc-recommendation-core

Journey Connect 추천 알고리즘의 **순수 Java 21 Core 1.0** 모듈입니다.

## 검증 원칙

- 운영 코드와 검증 코드는 Java 21만 사용합니다.
- Spring, JPA, Hibernate에 의존하지 않는 프레임워크 독립 코어입니다.
- Foundation부터 Wave 7까지 Java 계약 테스트를 실행합니다.
- 포팅 시 확정된 결과는 `src/test/resources/golden`에 Java 소유 golden fixture로 고정합니다.
- Node, Python, TypeScript oracle 또는 shell verifier를 CI에서 실행하지 않습니다.

## 실행

프로젝트의 `jc-backend` 디렉터리에서 실행합니다.

```powershell
.\gradlew.bat :jc-recommendation-core:check --stacktrace
```

`check`에는 다음 게이트가 포함됩니다.

- Foundation~Wave 7 계약 테스트
- Java oracle과 committed golden fixture의 exact match
- Spring/JPA/Hibernate 의존성 금지 검사
- Java 21 `-Xlint:all -Werror` 컴파일

Golden fixture는 정상적인 정책 변경이 승인된 경우에만 Java oracle 결과로 명시적으로 갱신해야 합니다.
