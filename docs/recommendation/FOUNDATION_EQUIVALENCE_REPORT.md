# Java 포팅 Foundation 동등성 검증 보고서

## 1. 판정

**현재 foundation 이식 범위: PASS**

TypeScript Phase 2.9b 참조 구현과 Java 21 구현의 동일 시나리오 출력을 비교했으며, 비교 범위 안에서는 문자열과 IEEE-754 raw bit 결과가 정확히 일치했다.

## 2. 검증 환경

| 항목 | 값 |
|---|---|
| Java | OpenJDK 21.0.10 |
| Node.js | v22.16.0 |
| npm | 10.9.2 |
| 참조 패키지 | `yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0` |
| 원본 ZIP SHA-256 | `e4bd7e79c5e539c6798fcfd25ed3cdfcda177fa4b6f1301e35892359535b63f5` |
| 참조 전체 테스트 | 65 files / 963 tests PASS |

## 3. exact oracle 비교 범위

- 기반 정책 7종의 version/effectiveFrom
- 이벤트 가중치 16종과 `report = null`
- feature vocabulary 42개 전체
- source priority 순서
- cold-start 가중치
- 후보 제한 100개 및 요청 제한 7개
- repeat decay occurrence 1, 2, 3, 100
- time decay 대표값과 7/30/90일 경계
- saturation 입력 -5, 0, 1, 5, 10, 95, 100

숫자는 decimal 문자열이 아니라 `Double.doubleToRawLongBits`와 JavaScript `DataView#getBigUint64` 결과를 비교했다.

검증 명령:

```bash
bash scripts/recommendation/verify_java_core_foundation.sh
```

기대 출력:

```text
Java recommendation core foundation contract: PASS
TypeScript ↔ Java foundation oracle: EXACT MATCH
```

## 4. 별도 Java 계약 테스트 범위

다음은 Java 계약 테스트를 통과했으나 아직 TS exact oracle에는 포함하지 않았다.

- 명시적 선호 → interest signal 변환
- unknown feature 거부
- strength 0..1 검증
- 사용자/feature 중복 선호 거부
- 반환 collection 불변성

해당 범위는 golden fixture exporter가 추가되는 다음 작업에서 언어 간 exact 비교 대상으로 승격한다.

## 5. 미완료 범위

다음 항목은 아직 Java로 포팅하지 않았다.

- state-event resolution
- interest match
- hard/soft context
- freshness
- popularity
- score composition
- base ranking
- diversity
- exploration
- exposure trace
- attribution
- replay
- offline evaluation

따라서 이 보고서는 **전체 알고리즘 동등성 보고서가 아니라 foundation 첫 배치 보고서**다.

## 6. 확인된 호환성 위험

1. TypeScript의 `localeCompare`와 Java 자연 문자열 비교가 비 ASCII 입력에서 달라질 수 있다.
2. JavaScript `Date.parse`와 Java `Instant.parse`가 허용하는 ISO 문자열 범위가 완전히 같지 않다.
3. JavaScript는 timestamp를 millisecond 단위로 계산하므로 Java nanosecond 입력의 정규화 규칙을 추가로 고정해야 한다.
4. canonical JSON과 signed zero는 별도 모듈에서 raw-byte 기준으로 검증해야 한다.
5. Node TypeScript 직접 실행은 현재 foundation oracle에서만 실험적 type stripping을 사용하며, 정식 golden exporter로 교체해야 한다.

위 위험은 이후 Wave 1 계약 고정 단계에서 해소한 뒤 scoring 포팅으로 진입한다.
