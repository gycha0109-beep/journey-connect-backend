# SC OP-3 Parallel External Preparation

## Status

```text
OP3_EXTERNAL_RESOLUTION_PARTIAL
SC_OP3_EXECUTION_APPROVED=NO
OP3_ENTRY=BLOCKED
FEATURE_FLAG=OFF
EFFECTIVE_NONPRODUCTION_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
```

This record allows work orders #37-#43 to advance in parallel while preserving the final AND gate. It records decisions, dependencies and preparation state. It is not actual external environment evidence and does not close any work order.

## Verified baseline

| Field | Value |
|---|---|
| Verified remote main | `25ad5148dd0e9eac2131f18b8020145b6389fc93` |
| Working branch | `agent/op3-operations-control-plane-preparation` |
| Prior exact head | `f69bf62ca7abde8bdb5afafd43b91f12134ae006` |
| Programme | `#36 OPEN` |
| Work orders | `#37-#43 OPEN` |
| Common approved environment ID | `UNASSIGNED` |

## SC or user decisions required

1. Assign one actual #43 manual operator or approved execution function.
2. Assign one independent approver that is not the executing actor.
3. Assign one incident commander or approved on-call function with stop authority.
4. Approve or reject the recommended dedicated non-production platform candidate.
5. Supply the cloud account/project or infrastructure repository in which the environment may be created.
6. Approve a region and cost-bearing resource owner.
7. Approve the workload-identity trust administrator and the bounded runtime/deployment roles.
8. Approve the allowlist storage owner and reviewer-access model.
9. Approve the metrics/dashboard administrator and alert receiver.
10. Confirm whether GitHub Actions artifact retention is acceptable as the retained OP evidence layer or whether a separately governed immutable store is required.

No repository owner, existing unrelated cloud project or application role is interpreted as one of these decisions.

## GitHub and code work immediately possible

- preserve exact-head governance and historical continuity checks;
- define one candidate source/protocol/version combination;
- define request/response compatibility and safety boundaries;
- define workload identity, TTL, audience and scope requirements;
- define default-deny allowlist semantics and audit metadata;
- define environment and network route acceptance inventory;
- define metrics backend, dashboard and panel-to-query resource inventory;
- create evidence envelope templates and protected-data review checks;
- add future provider-binding verification scripts without claiming provider execution;
- continue CI at traffic zero with feature flag OFF.

## Actual external environment access required

- create or identify the dedicated non-production cloud project/account;
- create a non-production service and immutable deployment/revision identity;
- create the HTTPS route and prove production deny;
- bind GitHub OIDC or another approved workload identity to a minimum-privilege cloud principal;
- issue and revoke a short-lived access token;
- create and exercise the default-deny allowlist store;
- create the metrics backend/workspace and deployed dashboard;
- configure real critical and warning alert delivery routes;
- execute route withdrawal/restoration and deployment rollback drills;
- verify evidence writer and independent reviewer access.

## #43 Operations control plane

Current state remains `UNASSIGNED / BLOCKED_EXTERNAL_DEPENDENCY`.

### Required assignments

| Item | State |
|---|---|
| Executing actor | `UNASSIGNED` |
| Independent approver | `UNASSIGNED` |
| Incident commander/on-call | `UNASSIGNED` |
| Shared non-production environment | `UNASSIGNED` |
| Manual enable path | `BLOCKED_EXTERNAL_DEPENDENCY` |
| Manual disable path | `BLOCKED_EXTERNAL_DEPENDENCY` |

### Evidence store candidate

`GITHUB_ACTIONS_ARTIFACTS_V4` is available as a content-immutable candidate with per-run artifact identity, URL and digest. Acceptance is not executed. A dedicated OP-3 workflow run, exact-head binding, retention proof, writer test, SC reviewer read test and administrative deletion-risk decision are still required.

This candidate does not resolve the missing operator, approver, incident command, environment or manual control path.

## #40 Platform environment and route candidate

### Investigation result

- No Kubernetes, Helm, Terraform, deployment manifest or managed-secret path exists in the application repository.
- The connected Vercel account has no Journey Connect project.
- Connected Supabase projects are not identified as Journey Connect dedicated environments and are not repurposed.
- CI-localhost and mock routes remain ineligible.

### Recommended candidate requiring approval

```text
ENVIRONMENT_CLASS=DEDICATED_GCP_CLOUD_RUN_NONPRODUCTION
ENVIRONMENT_ID=UNASSIGNED
CLOUD_PROJECT_ID=UNASSIGNED
REGION=UNASSIGNED
SERVICE_ID=UNASSIGNED
SOURCE_RUNTIME_ID=UNASSIGNED
DESTINATION_SERVICE_ID=UNASSIGNED
PROTOCOL=HTTPS
PORT=443
PATH=/v1/candidates/read
AUTHENTICATION=OIDC_IAM
PRODUCTION_ROUTE=FORBIDDEN
DATABASE_ROUTE=FORBIDDEN
REDIRECTS=FORBIDDEN
STATUS=RECOMMENDED_SC_DECISION_REQUIRED
```

The recommendation is architectural only. No cloud project, service, hostname, route or revision is represented as created.

### Route evidence still required

- immutable project/service/revision identifiers;
- approved source and destination identities;
- exact HTTPS 443 inventory;
- non-production binding proof;
- production route deny proof;
- successful connection;
- route withdrawal timestamp and failed post-withdrawal connection;
- controlled restoration and unchanged scope proof.

## #39 Security and Data access preparation

### Selected issuance path

```text
IDENTITY_ISSUER=https://token.actions.githubusercontent.com
FEDERATION=GITHUB_ACTIONS_OIDC_TO_APPROVED_CLOUD_WORKLOAD_IDENTITY
REPOSITORY_BINDING=gycha0109-beep/journey-connect-backend
GITHUB_ENVIRONMENT=op3-nonproduction
TARGET_AUDIENCE=UNASSIGNED
RUNTIME_PRINCIPAL=UNASSIGNED
DEPLOYMENT_PRINCIPAL=UNASSIGNED
TARGET_TTL_SECONDS=900
MAX_TTL_SECONDS=3600
LONG_LIVED_STATIC_SECRET=FORBIDDEN
PRODUCTION_SCOPE=FORBIDDEN
DATABASE_SCOPE=FORBIDDEN
WRITE_SCOPE=FORBIDDEN_FOR_RUNTIME_INVOKER
STATUS=READY_FOR_PROVIDER_BINDING_NOT_ISSUED
```

`op3-nonproduction` is a required GitHub Environment name, not a claim that its protection rules already exist.

### Scope separation

- Runtime invoker: invoke only the approved read-only candidate service.
- Deployment/drill actor: separate manually approved role limited to non-production revision deployment, inspection and rollback.
- Metrics reader/reviewer: read-only dashboard/query access.
- No principal receives production project, database, secret export or authority-transfer permission.

### Allowlist contract

```text
MODE=DEFAULT_DENY
IDENTITY=SYNTHETIC_OR_APPROVED_TEST_ACCOUNT_ONLY
STORED_REFERENCE=SHA256_TEST_SUBJECT_REF_ONLY
PURPOSE=READ_ONLY_CANDIDATE_COMPARISON
ENVIRONMENT_BINDING=REQUIRED
MAX_ENTRY_DURATION_DAYS=30
REMOVAL_AND_EXPIRY=FAIL_CLOSED
HASHED_AUDIT=REQUIRED
RAW_IDENTITY_STORAGE=FORBIDDEN
RAW_IDENTITY_LOGGING=FORBIDDEN
STORE_ID=UNASSIGNED
STATUS=CONTRACT_READY_STORE_NOT_CONNECTED
```

Actual issuance, successful access, revocation, post-revocation failure, reissue and allowlist expiry/removal evidence remain not executed.

## #41 Candidate source decision

One combination is selected for compatibility preparation:

```text
CANDIDATE_SOURCE=JOURNEY_CONNECT_RCA2_CANDIDATE_SHADOW_SERVICE
TRANSPORT_PROTOCOL=HTTPS_JSON
HTTP_METHOD=POST
PATH=/v1/candidates/read
API_SCHEMA_VERSION=recommendation-runtime-dark-read-v1
QUERY_REGISTRY_VERSION=recommendation-runtime-dark-read-query-registry-v1
OPERATION=READ_ONLY_CANDIDATE_COMPARISON
READ_ONLY=YES
IDEMPOTENT=YES
SERVING=FORBIDDEN
RETRY_POLICY=NONE
LATE_RESULT_POLICY=DISCARD
CONNECTION_TIMEOUT_MS=100
READ_TIMEOUT_MS=300
TOTAL_TIMEOUT_MS=500
MAX_REQUEST_BYTES=16384
MAX_RESPONSE_BYTES=65536
STATUS=DECIDED_NOT_CONNECTED
```

The source name is a logical owner-bound service identity. It is not a fabricated endpoint or deployed resource.

### Compatibility conditions

- request carries only privacy-safe hashes, lane, checkpoint hash and lineage fingerprint;
- response lane must equal the primary lane;
- incompatible or unresolved versions fail closed;
- timeout, exception, invalid payload and unavailable source retain the primary result;
- no retry and late results are discarded;
- candidate data is not served, persisted, cached or emitted;
- no database write, event emission, notification emission or ranking feedback occurs;
- actual integration must prove byte-equivalent primary response before and after candidate execution.

## #37 Observability preparation

The existing application boundary contains Micrometer counters, gauges and timers, a 27-metric continuity inventory and a 22-section dashboard contract. External observability remains absent.

### Required external resources

1. metrics project/workspace identifier;
2. metric export/scrape or OTLP path bound to the selected environment;
3. minimum-privilege metric writer identity;
4. deployed dashboard identifier;
5. panel-to-query mapping for every required Stage 1 metric;
6. runtime query references showing data or a controlled zero-data state without errors;
7. retention-policy reference;
8. cardinality-limit policy;
9. label privacy review showing no raw identity, token or protected value;
10. Observability Owner write access and SC reviewer read access evidence;
11. later alert-policy identifiers for #38.

### Candidate when the recommended environment is approved

```text
METRICS_BACKEND_CANDIDATE=GCP_CLOUD_MONITORING_OR_MANAGED_PROMETHEUS
DASHBOARD_CANDIDATE=GCP_CLOUD_MONITORING_DASHBOARD
WORKSPACE_ID=UNASSIGNED
DASHBOARD_ID=UNASSIGNED
STATUS=PLAN_READY_ENVIRONMENT_BLOCKED
```

No backend query or deployed dashboard evidence exists yet.

## Work-order dependency graph

| Work order | Current preparation | Direct dependencies before actual evidence |
|---|---|---|
| #43 Operations | request matrix and evidence-store candidate prepared | actual actors, #40 environment, manual control path |
| #40 Platform | one environment class and route contract recommended | SC/user cloud decision, project, region, service and network access |
| #39 Security/Data | OIDC, TTL, scope and allowlist contract prepared | #40 project/service, trust admin, allowlist store owner |
| #41 Recommendation | source/protocol/version singularly decided | #40 endpoint/route and #39 runtime identity |
| #37 Observability | resource and dashboard inventory prepared | #40 environment and deployed revision; metric writer from #39 |
| #38 Incident Response | application alert rules already prepared | #37 backend/dashboard, real receiver, IC from #43 |
| #42 Release | rollback contract exists but drill not executed | #40 deployable revisions/route, #39 deployment role, operator/approver/IC from #43 |

## Parallel execution order after external foundation exists

```text
#40 environment and route
+ #39 workload identity and allowlist
+ #43 human control plane
        ↓
#41 candidate adapter integration
        ↓
#37 runtime metric queries and dashboard
        ↓
#38 critical/warning alert delivery
        ↓
#42 deployment rollback drill
        ↓
independent evidence acceptance
        ↓
SC reassessment only
```

Preparation work may remain parallel. Actual execution must bind every result to one approved environment and an exact compatible revision set.

## Acceptance boundary

All work orders remain open. Repository or CI preparation is not substituted for external evidence. No traffic, feature flag, production route, candidate serving, write path, automatic rollout or authority transfer is enabled.