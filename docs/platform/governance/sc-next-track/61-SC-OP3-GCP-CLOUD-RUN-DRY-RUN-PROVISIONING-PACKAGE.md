# SC OP-3 GCP Cloud Run Reference Architecture

## Status

```text
PLATFORM_ARCHITECTURE_REFERENCE=GCP_CLOUD_RUN
PLATFORM_ARCHITECTURE_REFERENCE_STATUS=DESIGN_ONLY
GCP_ARCHITECTURE_STATUS=REFERENCE_ONLY

FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
GCP_PROVISIONING_PLANNED=NO
CLOUD_PROVISIONING_REQUIRED_NOW=NO
DEPLOYMENT_IMPLEMENTATION=DEFERRED

ACTUAL_EXECUTION=FORBIDDEN
RESOURCE_CREATION=FORBIDDEN
BILLING_CHANGE=FORBIDDEN
IAM_MUTATION=FORBIDDEN
ENDPOINT_CALL=FORBIDDEN
```

This file preserves the design knowledge created in PR #45. It is not a command package, an approved deployment plan or evidence that Google Cloud will be used.

## Portable control model

The following concepts remain valid regardless of the final platform:

- immutable deployment or revision identity bound to an exact repository revision;
- candidate deployment at zero traffic;
- private authenticated invocation;
- separated deployment and runtime identities;
- finite credentials and no long-lived static secret;
- default-deny allowlist using synthetic or approved test identities;
- no raw identity storage or logging;
- no production route and no database route;
- candidate non-serving and primary-result preservation;
- metrics, dashboard, alert and audit evidence;
- rollback, route-withdrawal and teardown verification;
- retained evidence with checksums and independent review.

## GCP-specific mapping retained for reference

If GCP were selected in a future independent decision, the reference mapping would use:

- Cloud Run immutable revisions and zero-traffic rollout;
- IAM-authenticated private invocation;
- Workload Identity Federation for short-lived GitHub Actions access;
- Cloud Monitoring or Managed Prometheus;
- Cloud Storage retention policy for authoritative evidence;
- Cloud Audit Logs for actor and data-access evidence.

These mappings are not selected resources. The former candidate region `asia-northeast3`, required APIs, naming patterns, service identities, project identifiers, buckets, dashboards and policies are all non-operative reference material.

```text
GCP_PROJECT_ID=NOT_APPLICABLE_REFERENCE_ONLY
GCP_PROJECT_NUMBER=NOT_APPLICABLE_REFERENCE_ONLY
GCP_REGION=NOT_APPLICABLE_REFERENCE_ONLY
CLOUD_RUN_SERVICE=NOT_APPLICABLE_REFERENCE_ONLY
CLOUD_RUN_REVISION=NOT_APPLICABLE_REFERENCE_ONLY
WORKLOAD_IDENTITY_POOL=NOT_APPLICABLE_REFERENCE_ONLY
WORKLOAD_IDENTITY_PROVIDER=NOT_APPLICABLE_REFERENCE_ONLY
EVIDENCE_BUCKET=NOT_APPLICABLE_REFERENCE_ONLY
MONITORING_WORKSPACE=NOT_APPLICABLE_REFERENCE_ONLY
```

## Retained safety properties

Any future platform implementation must preserve:

```text
FEATURE_FLAG=OFF
NONPRODUCTION_TRAFFIC=0
PRODUCTION_TRAFFIC=0
CANDIDATE_SERVING=FORBIDDEN
PRODUCTION_ROUTE=FORBIDDEN
DATABASE_ROUTE=FORBIDDEN
PUBLIC_UNAUTHENTICATED_ACCESS=FORBIDDEN
LONG_LIVED_STATIC_SECRET=FORBIDDEN
RETENTION_LOCK_AUTHORIZED=NO
```

## Translation requirement

The final platform design must translate each portable control to the actual academy environment rather than copying GCP resource names. AWS remains only an expected candidate pending curriculum, account, credit, payment-method, duration and teardown confirmation.

The disabled GCP shell and workflow examples remain historical reference artefacts. They must not be moved into an executable workflow, supplied with credentials or represented as a current provisioning path.
