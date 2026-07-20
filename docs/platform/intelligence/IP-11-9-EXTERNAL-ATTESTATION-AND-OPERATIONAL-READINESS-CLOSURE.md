# IP-11.9 External Attestation and Operational Readiness Closure

## Closed external evidence

- OpenJDK 21 and Gradle 8.14.5 offline execution PASS
- backend `test` and `check` PASS
- IP-9, IP-10 and IP-11.5 dedicated gates PASS
- P0 PASS, P1 17 scenarios PASS, P2 23 scenarios PASS
- Spring default/test/stage/prod protection contexts PASS
- PostgreSQL 15 clean SQL 01..28 replay PASS
- projection insert/update/private/moderation/delete regression PASS

## Remaining operational blockers transferred to IP-12

- actual production profile wiring
- property/restart kill-switch path
- Micrometer `MeterRegistry` adapter
- 10 BPS production ceiling
- actual account allowlist values
- production-equivalent disable drill

The external evidence is historical evidence for the IP-11.9 source SHA. IP-12 production source changes require a new Gradle/Spring attestation before traffic activation.
