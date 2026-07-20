# IP-11.5 Kill-switch and Internal Cohort Contract

## Evaluation order

`production guard -> kill-switch -> internal cohort -> sampling -> executor`.

## Kill-switch

- default: `DisabledSearchShadowKillSwitch` = KILLED
- missing/blank/malformed/property supplier failure = KILLED
- approval absent = KILLED
- `prod`/`production` profile blocks technical capability graph before switch evaluation
- current property capability requires restart; remote/dynamic toggle is not implemented

## Cohort

- default: `EmptyProductionShadowCohortSelector`, effective cohort 0%
- allowlist accepts only pre-hashed 64-hex internal fixture keys
- malformed list or approval false becomes empty
- raw identity is never emitted to metrics/evidence

## Sampling

- production default/missing/approval absent: 0 bps
- IP-11.5 refuses creation of non-zero `productionApproved` sampling
- `technicalTestOverride` may use 10,000 bps only under explicit technical-test profile
- proposed future steps 10/50/100 bps remain unapproved

## Emergency drill

Direct test executes killed -> explicit technical fixture -> one async dispatch -> kill -> zero subsequent side effects. Repeated kill is safe. Metric/control exceptions fail closed and preserve legacy object identity.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
