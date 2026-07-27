# ADM-0 Admin Baseline Package

```text
STAGE=ADM-0
NAME=Admin Capability, Schema and Repository Integration Baseline
STATUS=ADMIN_CAPABILITY_SCHEMA_AND_INTEGRATION_BASELINE_ESTABLISHED
IMPLEMENTATION=NOT_STARTED
ADM1_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

## Authority

- Backend authority: `gycha0109-beep/journey-connect-backend` at work-start `main` `251f2d14c91c6e5bebb9dcb245aa8b1d7e859976`
- Admin UI reference: `YTAK99/Journey-Connect` branch `youngtak` final verified head `44435f04df439647d282bd15ae960349d0ee5f84` (initial intake `e2c2c283e7f10e32806d4fb5285081e7254b5782`)
- Work branch: `agent/adm0-admin-capability-schema-integration-baseline`
- Full source branch merge: `FORBIDDEN`
- Selective UI port: `YES`
- `SELECTIVE_UI_PORT=YES`
- Backend first: `YES`
- Final team repository sync: `LAST_STEP`

## Required output map

The requested 25 documents are preserved as independently reviewable sections or dedicated handoff documents.

| # | Required document | Location |
|---:|---|---|
| 1 | ADM-0 entry verification | `ADM-0-ENTRY-VERIFICATION.md` |
| 2 | backend authoritative baseline | Baseline §2 |
| 3 | Youngtak source intake | Baseline §3 |
| 4 | Admin UI reuse assessment | Baseline §4 |
| 5 | Admin MVP capability matrix | Baseline §5 |
| 6 | deferred/forbidden matrix | Baseline §6 |
| 7 | current backend domain inventory | Baseline §7 |
| 8 | security and role inventory | Baseline §8 |
| 9 | current schema inventory | Baseline §9 |
| 10 | schema gap analysis | Baseline §10 |
| 11 | proposed migration design | Baseline §11 |
| 12 | Admin API contract | Baseline §12 |
| 13 | moderation state machine | Baseline §13 |
| 14 | audit contract | Baseline §14 |
| 15 | data privacy/redaction contract | Baseline §15 |
| 16 | frontend decomposition plan | Baseline §16 |
| 17 | repository responsibility matrix | Baseline §17 |
| 18 | branch and PR workflow | Baseline §18 |
| 19 | final repository synchronisation plan | Baseline §19 |
| 20 | dependency graph | Baseline §20 |
| 21 | risk register | Baseline §21 |
| 22 | blocker register | Baseline §22 |
| 23 | ADM-1 entry gate | `ADM-1-ENTRY-GATE-AND-HANDOFF.md` |
| 24 | ADM-1 handoff | `ADM-1-ENTRY-GATE-AND-HANDOFF.md` |
| 25 | ADM-1 implementation prompt | `ADM-1-IMPLEMENTATION-PROMPT.md` |

Machine-readable artifacts are bundled by stable artifact name in `verification/admin/adm0/adm0-artifacts.json`. The independent verifier emits exact-head evidence under `verification/admin/adm0/evidence/` during CI.
