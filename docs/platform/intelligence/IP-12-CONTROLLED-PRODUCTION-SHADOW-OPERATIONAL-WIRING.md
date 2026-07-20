# IP-12 Controlled Production Shadow Operational Wiring

## State

`IMPLEMENTATION_COMPLETE / EXTERNAL_ATTESTATION_PENDING`

Production shadow remains `DISABLED`; effective production sampling remains `0 BPS`; actual cohort remains empty; Search cutover remains not started.

## Runtime boundary

```text
GET /api/v1/explore
→ PostService.explore exactly once
→ legacy PageResponse fixed
→ existing IP-9 ExploreSearchShadowBridge
→ production activation gate
→ kill-switch
→ sampling/cohort/account checks
→ bounded asynchronous executor
→ projection-only Search Runtime
→ comparison + Micrometer + no-op evidence
→ identical legacy PageResponse returned immediately
```

Controller, PostService, Repository, DTO and SecurityConfig were not changed. The production adapter is selected only for `prod` or `production`. Default/test contexts remain disabled and stage wiring remains isolated.

## Actual changed production files

- backend production shadow adapter/configuration package
- `ProductionShadowTaskExecutor` and approved pilot resource contract
- production profile resources with fail-closed defaults
- backend Gradle dependency/task declarations

No SQL or evidence persistence was added.
