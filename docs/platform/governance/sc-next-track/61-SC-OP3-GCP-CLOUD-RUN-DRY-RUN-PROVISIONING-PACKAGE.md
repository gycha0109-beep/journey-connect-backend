# SC OP-3 GCP Cloud Run Dry-run Provisioning Package

## Control header

```text
EXECUTION_MODE=TEMPLATE_ONLY
ACTUAL_EXECUTION=FORBIDDEN
PROJECT_ID=REQUIRED_INPUT
REGION=REQUIRED_INPUT
BILLING_CHANGE=FORBIDDEN
IAM_MUTATION=FORBIDDEN
RESOURCE_CREATION=FORBIDDEN
ENDPOINT_CALL=FORBIDDEN
```

This package is a non-executing design and command-rendering template. It does not authenticate to Google Cloud and does not call `gcloud` or any Google API.

## GCP project prerequisites

### Required inputs

- `gcp_project_id`
- `billing_resource_owner`
- `region_final_approval`
- `cloud_platform_administrator`
- `workload_identity_administrator`
- `cost_ceiling`
- `teardown_deadline`

All remain governed by the required-input matrix. The candidate region is `asia-northeast3`; it is not final until the cost/resource owner approves it.

### Required APIs after separate authorisation

```text
run.googleapis.com
artifactregistry.googleapis.com
iam.googleapis.com
iamcredentials.googleapis.com
sts.googleapis.com
cloudresourcemanager.googleapis.com
serviceusage.googleapis.com
storage.googleapis.com
monitoring.googleapis.com
logging.googleapis.com
cloudbilling.googleapis.com
```

`cloudbuild.googleapis.com` is not included in the baseline because the template assumes a separately supplied immutable container image digest. Source builds require a separate scope and cost decision.

### Project and billing prerequisites

- project must be dedicated to Journey Connect OP-3 non-production work or be explicitly certified as dedicated;
- project ID and project number must be recorded separately;
- billing must already be linked by an authorised owner; this package cannot link it;
- a budget alert must be designed before resource creation;
- a budget is notification control, not a hard spending cap;
- resource creation remains forbidden until a cost ceiling and teardown deadline are supplied;
- organization policies and quotas must be reviewed before enabling APIs.

### Region binding

- candidate: `asia-northeast3`;
- final region: required input;
- Cloud Run service, Artifact Registry image location, monitoring scope and evidence bucket location must be reviewed for compatible placement;
- no cross-region production route is permitted.

### Naming rules

All concrete names are required inputs. The template uses patterns only:

```text
service:      jc-op3-rca2-np-${SUFFIX}
revision:     ${SERVICE}-${EXACT_SHA_PREFIX}-${SEQUENCE}
runtime SA:   jc-op3-runtime-${SUFFIX}
deploy SA:    jc-op3-deploy-${SUFFIX}
WIF pool:     jc-op3-gha-${SUFFIX}
WIF provider: jc-op3-repo-${SUFFIX}
bucket:       ${PROJECT_ID}-jc-op3-evidence-${SUFFIX}
dashboard:    JC OP3 RCA2 Non-production
```

`SUFFIX`, project ID, service accounts and bucket names must be supplied and reviewed. These patterns do not claim resources exist.

### Environment labels

```text
journey-connect-environment=op3-nonproduction
journey-connect-workstream=rca2
journey-connect-authority=current-p1-p2-only
journey-connect-candidate-serving=forbidden
journey-connect-traffic=zero
journey-connect-cost-owner=required-input
journey-connect-teardown-deadline=required-input
```

No raw user, test identity, token, secret or personal data may be used as a label.

### Production separation

- separate project or formally certified isolated project;
- no production service account;
- no production VPC, connector, database or endpoint;
- no production Artifact Registry write;
- no production monitoring mutation;
- no shared bucket;
- no public ingress or `allUsers` binding;
- no existing K-beauty or Supabase resource reuse.

## Cloud Run design

### Service and revision

- exactly one dedicated non-production Cloud Run service for the initial package;
- immutable container image digest required;
- immutable revision name bound to exact Git revision;
- new revision deployed with `0%` traffic;
- minimum instances `0`;
- request-based billing;
- approved maximum instances required;
- automatic rollout forbidden;
- candidate revision tag requires a cost review because tagged revisions can affect instance behaviour;
- unauthenticated/public access forbidden.

### Endpoint contract

```text
METHOD=POST
PATH=/v1/candidates/read
AUTHENTICATION=OIDC_IAM
DATABASE_ROUTE=FORBIDDEN
PRODUCTION_ROUTE=FORBIDDEN
REDIRECTS=FORBIDDEN
CANDIDATE_SERVING=FORBIDDEN
RETRY=NONE
```

The runtime response remains the current P1/P2 response. Candidate output is comparison-only and must not mutate, replace, blend or enrich the served response.

### Non-executing command rendering

The shell template is:

`verification/sc-next-track/op3-entry/templates/op3-gcp-cloud-run-template-only.sh`

It fails when required inputs are absent and prints `WOULD_RUN` command lines only. It contains no `gcloud` execution path.

## Workload identity design

### Trust source

```text
ISSUER=https://token.actions.githubusercontent.com
REPOSITORY=gycha0109-beep/journey-connect-backend
GITHUB_ENVIRONMENT=op3-nonproduction
TARGET_TTL_SECONDS=900
LONG_LIVED_STATIC_SECRET=FORBIDDEN
```

Required immutable claim bindings:

- GitHub repository ID;
- GitHub repository owner ID;
- approved workflow reference;
- approved branch/ref or environment subject;
- audience bound to the approved Workload Identity Provider.

Repository-name-only trust is insufficient. Numeric repository and owner identifiers are required inputs because they cannot be reused like names.

### GitHub workflow conditions

The template requires:

- `id-token: write`;
- `contents: read`;
- an approved GitHub Environment;
- environment protection and reviewer requirements;
- exact reusable workflow or workflow path binding;
- exact repository ID and owner ID conditions;
- branch/ref condition appropriate to the execution workflow;
- no pull-request-from-fork authentication;
- no credential JSON secret.

The non-runnable workflow example is:

`verification/sc-next-track/op3-entry/templates/op3-github-actions-oidc-template-only.yml.example`

### TTL

The contract target is `900` seconds and the maximum contract ceiling remains `3600` seconds. Actual GitHub OIDC and derived token lifetimes must be measured and recorded during #39; configuration text alone is not evidence.

### Principal separation

| Principal | Allowed future scope | Forbidden |
|---|---|---|
| Deploy/drill principal | non-production Cloud Run deploy, revision inspect, traffic-zero validation, authorised rollback | runtime invocation authority beyond drill, production access, billing mutation |
| Runtime invoker | invoke the one approved candidate Cloud Run service | deploy, IAM mutation, Storage administration, database access |
| Evidence writer | create evidence objects in the one approved bucket prefix | delete/overwrite, bucket policy or retention mutation |
| Metric writer | write approved bounded metrics | dashboard/IAM/alert administration |
| SC reviewer | read evidence, dashboard and audit metadata | write/delete/mutate resources |

Exact IAM roles are selected only after a cloud administrator reviews the minimum permissions. Broad project Owner/Editor roles are forbidden for workload principals.

## Allowlist design

```text
MODE=DEFAULT_DENY
IDENTITY_MODE=SYNTHETIC_OR_APPROVED_TEST_ACCOUNT_ONLY
STORED_REFERENCE=SHA256_ONLY
PURPOSE=READ_ONLY_CANDIDATE_COMPARISON
EXPIRY_AND_REMOVAL=FAIL_CLOSED
RAW_IDENTITY_STORAGE=FORBIDDEN
RAW_IDENTITY_LOGGING=FORBIDDEN
```

Required audit events:

- entry created;
- entry read for authorisation decision;
- entry expired;
- entry removed;
- access allowed;
- access denied.

Each event must include environment, purpose, actor/principal, exact revision, timestamp, decision and hashed reference only.

The storage technology remains `UNASSIGNED`; no database, bucket or cache is implied.

## Evidence store design

### Transport

GitHub Actions Artifacts v4 carries intermediate evidence only.

### Authoritative bucket

The target is one dedicated Cloud Storage bucket with:

- uniform bucket-level access;
- public access prevention;
- retention period supplied by `evidence_retention_period_days`;
- object names bound to work order, exact SHA, run and timestamp;
- SHA-256 digest in evidence manifest and transport metadata;
- versioned evidence envelope;
- execution-process create permission;
- SC reviewer read permission;
- no overwrite or delete permission for the execution writer;
- Admin Activity audit logs;
- Data Read and Data Write audit logs enabled;
- detailed audit logging review;
- no lifecycle rule that deletes objects before retention expiry.

Proposed object path pattern:

```text
op3/work-order-${WORK_ORDER}/sha-${EXACT_SHA}/run-${RUN_ID}/${UTC_TIMESTAMP}/${ARTIFACT_NAME}
```

All variables are required external values. No object or bucket is represented as existing.

### Lock gate

Before a retention lock:

1. project and bucket owner assigned;
2. retention period approved;
3. cost and storage growth reviewed;
4. test objects written and read;
5. reviewer read access proven;
6. execution writer overwrite/delete denied;
7. audit logs verified;
8. project-deletion and lien impact accepted;
9. teardown and long-term evidence ownership separated;
10. separate explicit `BUCKET_RETENTION_LOCK_AUTHORIZED=YES`.

Current state remains `NOT_AUTHORIZED`.

## Monitoring and alerting design

### Backend

- Cloud Monitoring is the baseline;
- Managed Service for Prometheus may be used if the Micrometer/OpenTelemetry export design requires it;
- one approved metric writer with no resource-administration permission;
- bounded labels and no raw identity, token, URL query or secret;
- one custom dashboard resource;
- one panel-to-query mapping file;
- one controlled zero-data verification showing valid queries and no missing-resource errors;
- dashboard read access for SC reviewer.

### Dashboard evidence

For every required panel:

- metric type;
- resource type;
- query;
- aggregation and alignment;
- expected zero-data result;
- expected non-zero controlled result;
- dashboard widget ID;
- tested time range;
- exact tested revision;
- screenshot or API resource reference as secondary evidence;
- immutable query result manifest as primary evidence.

### Alert delivery

- critical and warning receivers supplied by the alert receiver owner;
- synthetic trigger;
- policy ID;
- incident ID;
- delivery timestamp;
- receiver acknowledgement timestamp;
- escalation timestamp where applicable;
- resolved timestamp;
- actor and exact revision;
- protected-data review.

No receiver is currently assigned and no alert has been delivered.

## Acceptance boundary

This package does not satisfy #37, #38, #39, #40, #41, #42 or #43. It only makes the approved architecture executable after all required inputs and separate mutation authorisations exist.
