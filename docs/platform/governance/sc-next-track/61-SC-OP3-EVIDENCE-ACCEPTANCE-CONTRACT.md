# SC OP-3 Evidence Acceptance Contract

## Rule

OP-3 external work orders are accepted only through evidence that is attributable, reproducible, environment-bound and retained. A claim, checklist tick or issue closure is not sufficient.

## Mandatory evidence envelope

Every work order must provide:

1. work-order issue number
2. accountable role and executing actor
3. exact tested application or configuration revision
4. non-production environment identifier
5. start and end timestamps
6. commands, queries or procedures used
7. expected and actual result
8. immutable artifact, run or resource reference
9. protected-data review result
10. reviewer and acceptance timestamp

## Evidence classes

| Class | Minimum acceptable evidence |
|---|---|
| Observability | backend query result, dashboard panel mapping, retention and label review |
| Incident response | synthetic alert delivery, acknowledgement and escalation record |
| Security/Data | bounded-access metadata, revocation result, allowlist expiry and audit result |
| Platform | route inventory, production-deny result, route withdrawal and restoration record |
| Recommendation | source/version decision, schema compatibility and non-serving integration result |
| Release | rollback start/stop record, restored artifact identity and health result |
| Operations | named operator and approver, manual control path and retained execution template |

## Automatic rejection conditions

Evidence is rejected when any of the following applies:

- environment identity is absent or ambiguous
- only mock, localhost or static configuration evidence is supplied
- a protected value is embedded in the repository or issue
- production access is used
- the tested revision is not recorded
- timestamps or actor identity are absent
- result-serving, primary mutation or authority transfer occurs
- the evidence cannot be retained or independently reviewed

## Gate impact

- one rejected or missing evidence item keeps its work order open
- one open work order keeps `OP3_ENTRY=BLOCKED`
- all accepted work orders permit reassessment only
- a separate SC decision is required to set `SC_OP3_EXECUTION_APPROVED=YES`
