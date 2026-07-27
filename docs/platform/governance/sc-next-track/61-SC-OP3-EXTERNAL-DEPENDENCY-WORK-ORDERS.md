# SC OP-3 External Dependency Work Orders

## Current governance classification

```text
FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
CLOUD_PROVISIONING_REQUIRED_NOW=NO
OP3_CLOUD_PROVISIONING=DEFERRED_PLATFORM_UNDECIDED
```

Work orders #37-#43 remain open for eventual external evidence. Their cloud-specific inputs are deferred until a final platform and funded execution path are selected.

## Work-order allocation

| Issue | Accountable role | Current state |
|---|---|---|
| #37 | Observability Owner | `DEFERRED_PLATFORM_BINDING` |
| #38 | Incident Response Owner | `DEFERRED_UNTIL_EXECUTION` |
| #39 | Security/Data Owner | `DEFERRED_PLATFORM_BINDING` |
| #40 | Platform Owner | `DEFERRED_PLATFORM_UNDECIDED` |
| #41 | Recommendation Owner | `APPROVED_NOT_CONNECTED` |
| #42 | Release Owner | `DEFERRED_UNTIL_DEPLOYMENT` |
| #43 | Operations Owner | `DEFERRED_UNTIL_EXECUTION` |

## Completion rule

A deferred state is not completion. Each work order still requires attributable, environment-bound and independently reviewable evidence before it can close.

## Current non-requirement

Missing GCP project, Cloud Run, Workload Identity, monitoring or bucket identifiers do not make the governance correction incomplete because GCP is reference-only and cloud provisioning is not required now.

Missing operator or independent approver assignments also do not block governance-only repository changes. Those roles become mandatory before actual resource mutation, traffic drill or acceptance.

## Prohibited shortcuts

- selecting AWS solely because it is expected in the academy curriculum;
- treating reference GCP names as deployable resources;
- using personal billing or payment details;
- repurposing K-beauty or existing Supabase resources;
- closing issues without accepted evidence;
- enabling traffic or candidate serving.

## Preserved state

```text
OP3_ENTRY=BLOCKED
STAGE1_ENABLEMENT=BLOCKED
FEATURE_FLAG=OFF
EFFECTIVE_NONPRODUCTION_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
CANDIDATE_SERVING=FORBIDDEN
```
