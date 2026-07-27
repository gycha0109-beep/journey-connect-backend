# SC OP-3 Cost and Teardown Boundary

## Status

```text
CLOUD_RESOURCE_CREATION_AUTHORIZED=NO
BILLING_SPEND_AUTHORIZED=NO
CLOUD_PROVISIONING_STATUS=BLOCKED_REQUIRED_INPUTS
BILLING_RESOURCE_OWNER=UNASSIGNED
COST_CEILING=UNASSIGNED
TEARDOWN_DEADLINE=UNASSIGNED
```

No cost-bearing resource may be created until every required input and separate provisioning approval is recorded.

## Required cost decisions

| Decision | Current value |
|---|---|
| Billing/resource owner | `UNASSIGNED` |
| Maximum permitted cost | `UNASSIGNED` |
| Currency and billing account scope | `UNASSIGNED` |
| Budget alert thresholds | `UNASSIGNED` |
| Experiment end date | `UNASSIGNED` |
| Teardown deadline | `UNASSIGNED` |
| Teardown operator | `UNASSIGNED` |
| Independent teardown reviewer | `UNASSIGNED` |
| Evidence retention period | `UNASSIGNED` |
| Evidence bucket long-term owner | `UNASSIGNED` |

A Google Cloud budget alert does not automatically cap usage or spending. It is a notification control. The cost owner must separately select service limits, resource ceilings and a stop procedure.

## Resource allowlist after separate authorisation

Only the following resource classes may enter a future execution plan:

1. one dedicated non-production Google Cloud project, or one explicitly certified existing dedicated project;
2. required Google APIs only;
3. one Cloud Run service with one zero-traffic candidate revision;
4. one bounded Workload Identity Pool/provider and separated workload principals;
5. one default-deny allowlist store;
6. one Cloud Monitoring dashboard and required alert policies;
7. one Cloud Storage evidence bucket with an initially unlocked retention policy;
8. one budget alert.

Every concrete resource requires an owner, exact ID, region/location, cost classification and teardown treatment.

## Forbidden resource classes

- production project, account, route, identity or endpoint;
- public unauthenticated Cloud Run access;
- Cloud SQL or another database route;
- VPC connector, NAT or private network expansion without separate approval;
- external load balancer or custom domain;
- GPU, always-on VM or unrelated compute;
- Cloud Run minimum instances greater than zero;
- instance-based Cloud Run billing;
- unbounded autoscaling;
- long-lived service-account key;
- bucket retention lock without separate approval;
- reuse of existing K-beauty or Supabase resources;
- any resource not listed in the approved resource inventory.

## Cloud Run cost controls

Future configuration must use:

```text
BILLING_MODE=REQUEST_BASED
MIN_INSTANCES=0
MAX_INSTANCES=REQUIRED_INPUT
CANDIDATE_TRAFFIC_PERCENT=0
AUTOMATIC_ROLLOUT=FORBIDDEN
```

Tagged revisions require a separate cost check. Minimum instances and instance-based billing can incur charges while idle, so they are not approved.

## Budget contract

Before provisioning:

- assign the cost owner;
- record the approved amount and currency without conversion;
- select alert thresholds;
- select notification recipients;
- record the budget resource ID after creation;
- define the response at each threshold;
- define the manual stop authority;
- record that budget alerts are not hard caps.

Automatic billing disablement is not approved because it can have wider project impact. Any automated spend control requires separate SC and owner approval.

## Teardown contract

The future teardown procedure must:

1. prove candidate traffic is `0%`;
2. prove feature flag remains `OFF`;
3. export final evidence through GitHub Actions Artifacts v4;
4. write and checksum accepted evidence in the authoritative bucket;
5. revoke workload access;
6. remove allowlist entries;
7. disable/delete alert and dashboard resources after evidence capture;
8. remove Cloud Run tags, revisions and service according to approved order;
9. disable APIs only after confirming no retained dependency;
10. verify no production resource or authority changed;
11. record all deletion/retention outcomes in immutable evidence;
12. obtain independent reviewer acceptance.

`TEARDOWN_VERIFICATION=NOT_EXECUTED`.

## Retention-locked evidence bucket lifecycle

The evidence bucket has a lifecycle separate from the disposable experiment resources.

Before lock authorisation:

- assign a long-term bucket owner;
- approve retention days;
- estimate storage and audit-log cost;
- test write/read/audit controls;
- verify object naming and digest;
- review project deletion impact;
- review the lien created by Bucket Lock;
- decide who remains accountable after the experiment project teardown.

A locked retention policy cannot be shortened or removed. A project containing a locked bucket can be prevented from deletion by a lien. Therefore the bucket may require a separate long-lived evidence project rather than the disposable runtime project. That project topology is not yet approved.

## Acceptance boundary

Cost documentation is preparation only. It does not authorise project creation, billing attachment, API enablement, IAM mutation, bucket creation, retention configuration or Cloud Run deployment.
