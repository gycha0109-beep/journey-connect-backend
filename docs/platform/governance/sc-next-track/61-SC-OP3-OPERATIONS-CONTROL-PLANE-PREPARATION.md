# SC OP-3 Operations Control Plane Preparation

## Current timing decision

```text
WORK_ORDER_43_STATUS=DEFERRED_UNTIL_EXECUTION
CLOUD_PROVISIONING_REQUIRED_NOW=NO
INDEPENDENT_APPROVER_REQUIRED_NOW=NO
INDEPENDENT_APPROVER_STATUS=DEFERRED_UNTIL_EXECUTION
```

Work order #43 remains defined and open. Its execution controls are not mandatory inputs for a governance-only change that creates no resource, changes no IAM policy, performs no drill and serves no traffic.

## Roles retained for future execution

Before any actual cloud mutation, traffic drill or operational acceptance, SC must bind:

1. one manual operator;
2. one independent approver who is not the operator;
3. one incident commander or approved on-call function with stop authority;
4. one selected non-production environment;
5. least-privilege access with finite lifetime;
6. manual enable and disable paths;
7. an authoritative retained-evidence location;
8. a non-bypassable two-person confirmation mechanism.

Current values are intentionally deferred:

```text
MANUAL_OPERATOR=DEFERRED_UNTIL_EXECUTION
INDEPENDENT_APPROVER=DEFERRED_UNTIL_EXECUTION
INCIDENT_COMMANDER_OR_ON_CALL=DEFERRED_UNTIL_EXECUTION
NONPRODUCTION_ENVIRONMENT=DEFERRED_PLATFORM_UNDECIDED
MANUAL_ENABLE_PATH=DEFERRED_UNTIL_EXECUTION
MANUAL_DISABLE_PATH=DEFERRED_UNTIL_EXECUTION
AUTHORITATIVE_EVIDENCE_STORE=DEFERRED_PLATFORM_UNDECIDED
```

## Evidence transport

GitHub Actions Artifacts v4 remains an intermediate transport only. The authoritative evidence store must be selected with the final platform and actual execution plan. A GCP Cloud Storage bucket is reference-only, not the current selected store.

## Preserved safety boundary

```text
SC_OP3_EXECUTION_APPROVED=NO
FEATURE_FLAG=OFF
NONPRODUCTION_TRAFFIC=0
PRODUCTION_TRAFFIC=0
CANDIDATE_SERVING=FORBIDDEN
RETENTION_LOCK_AUTHORIZED=NO
```

No two-person approval action is required or executed for this governance correction. The role becomes blocking before the first real mutation or acceptance event.
