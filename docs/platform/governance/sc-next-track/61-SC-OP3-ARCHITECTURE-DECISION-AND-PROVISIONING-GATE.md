# SC OP-3 Architecture Decision and Provisioning Gate

## Decision status

```text
OP3_EXTERNAL_RESOLUTION_PARTIAL

PLATFORM_ARCHITECTURE=GCP_CLOUD_RUN
PLATFORM_ARCHITECTURE_STATUS=SC_APPROVED_NOT_PROVISIONED

REGION_CANDIDATE=asia-northeast3
REGION_STATUS=PENDING_COST_AND_RESOURCE_OWNER

CANDIDATE_CONTRACT_DECISION=APPROVED_NOT_CONNECTED

EVIDENCE_TRANSPORT=GITHUB_ACTIONS_ARTIFACTS_V4
EVIDENCE_TRANSPORT_STATUS=SC_APPROVED_INTERMEDIATE_ONLY

AUTHORITATIVE_EVIDENCE_STORE=GCP_CLOUD_STORAGE_RETENTION_POLICY_BUCKET
EVIDENCE_STORE_STATUS=DESIGN_APPROVED_NOT_PROVISIONED
RETENTION_LOCK_STATUS=NOT_AUTHORIZED

CLOUD_PROVISIONING_STATUS=BLOCKED_REQUIRED_INPUTS
CLOUD_RESOURCE_CREATION_AUTHORIZED=NO
BILLING_SPEND_AUTHORIZED=NO
SC_OP3_EXECUTION_APPROVED=NO
```

This is an architecture and preparation decision only. It does not identify or create a Google Cloud project, billing relationship, Cloud Run service, workload identity pool, IAM binding, bucket, monitoring workspace, endpoint, revision or route.

## Preserved execution boundary

```text
OP3_ENTRY=BLOCKED
STAGE1_ENABLEMENT=BLOCKED
FEATURE_FLAG=OFF
EFFECTIVE_NONPRODUCTION_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
CANDIDATE_SERVING=FORBIDDEN
AUTOMATIC_ROLLOUT=FORBIDDEN
AUTHORITY_TRANSFER=FORBIDDEN
```

Work orders #37 through #43 remain open and incomplete. GitHub or repository preparation is not accepted as external runtime evidence.

## Approved architecture target

The approved target is one dedicated, isolated, non-production Cloud Run environment. The service must:

- use a separately approved Google Cloud project;
- use final region approval before provisioning;
- remain private and IAM-authenticated;
- create immutable revisions;
- deploy a new candidate revision at zero traffic;
- expose only the approved read-only path `/v1/candidates/read`;
- have no production route and no database route;
- reject redirects;
- never serve candidate results to users;
- preserve current P1/P2 output authority;
- use request-based billing, minimum instances `0`, and an approved maximum instance ceiling;
- remain unavailable for execution until actor, cost, IAM, evidence and teardown inputs are assigned.

Cloud Run creates an immutable revision for each deployment or configuration change and supports a revision receiving no traffic. These platform properties are design inputs, not evidence that any revision currently exists.

## Region decision

`asia-northeast3` is the candidate region. Final region binding remains pending both:

1. an assigned billing/resource owner; and
2. explicit confirmation that the cost and resource lifecycle are accepted.

No resource name may embed or imply an approved region until that approval exists.

## Candidate contract decision

The following compatibility combination is approved but not connected:

```text
CANDIDATE_SOURCE=JOURNEY_CONNECT_RCA2_CANDIDATE_SHADOW_SERVICE
TRANSPORT_PROTOCOL=HTTPS_JSON
HTTP_METHOD=POST
PATH=/v1/candidates/read
API_SCHEMA_VERSION=recommendation-runtime-dark-read-v1
QUERY_REGISTRY_VERSION=recommendation-runtime-dark-read-query-registry-v1
OPERATION=READ_ONLY_CANDIDATE_COMPARISON
SERVING=FORBIDDEN
STATUS=APPROVED_NOT_CONNECTED
```

This source identifier is logical. It is not a hostname, deployed service, service account or endpoint.

## Evidence architecture

### Intermediate transport

GitHub Actions Artifacts v4 is approved only as the intermediate evidence transport. A workflow artifact may carry exact-head evidence to the authoritative store after external execution is separately approved.

It is not the authoritative long-term evidence store and does not satisfy #43 by itself.

### Authoritative store

The design target is a dedicated Cloud Storage bucket with a retention policy. The bucket is not provisioned.

The design requires:

- one dedicated bucket in an approved project and region/location;
- a variable retention period supplied as a required input;
- SHA-256 checksum metadata;
- exact tested revision and actor metadata;
- write access only for the approved execution process;
- read access for the SC reviewer;
- Cloud Audit Logs Admin Activity and Data Access coverage;
- detailed audit logging review;
- an explicit pre-lock verification and approval gate.

`RETENTION_LOCK_STATUS=NOT_AUTHORIZED`.

Locking a bucket retention policy is irreversible, prevents reducing or removing the policy, can prevent bucket deletion while objects remain retained, and can create a project lien. The project, retention period, cost owner and deletion impact must therefore be approved before any lock action.

## Required-input source

The authoritative required-input matrix is:

`verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json`

No `UNASSIGNED`, `REQUIRED_INPUT` or pending value may be inferred from repository ownership, existing unrelated Vercel/Supabase resources, placeholder strings, or GitHub artifact metadata.

## Provisioning control

All provisioning material in this phase is template-only:

```text
EXECUTION_MODE=TEMPLATE_ONLY
ACTUAL_EXECUTION=FORBIDDEN
PROJECT_ID=REQUIRED_INPUT
REGION=REQUIRED_INPUT
BILLING_CHANGE=FORBIDDEN
IAM_MUTATION=FORBIDDEN
RESOURCE_CREATION=FORBIDDEN
```

The templates may validate and render proposed commands. They must not invoke `gcloud`, Google APIs, Cloud Run endpoints, bucket operations, IAM operations or billing operations.

## Official design references

- Cloud Run revisions and zero-traffic deployment: https://cloud.google.com/run/docs/managing/revisions
- Cloud Run rollouts and `--no-traffic`: https://cloud.google.com/run/docs/rollouts-rollbacks-traffic-migration
- Cloud Run authentication: https://cloud.google.com/run/docs/authenticating/overview
- Cloud Run service identity: https://cloud.google.com/run/docs/securing/service-identity
- Workload Identity Federation for deployment pipelines: https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines
- Cloud Storage Bucket Lock: https://cloud.google.com/storage/docs/bucket-lock
- Cloud Storage audit logging: https://cloud.google.com/storage/docs/audit-logging
- Cloud Monitoring dashboards API: https://cloud.google.com/monitoring/dashboards/api-dashboard
- Cloud Billing budgets: https://cloud.google.com/billing/docs/how-to/budgets
