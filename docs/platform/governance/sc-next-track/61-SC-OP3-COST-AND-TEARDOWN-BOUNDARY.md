# SC OP-3 Cost and Teardown Boundary

## Current cost decision

```text
FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
CLOUD_PROVISIONING_REQUIRED_NOW=NO
CLOUD_PROVISIONING_STATUS=DEFERRED_PLATFORM_UNDECIDED

PERSONAL_CLOUD_SPEND_ALLOWED=NO
PAID_CLOUD_USAGE=FORBIDDEN
COST_CEILING=0
BILLING_ACCOUNT_LINKAGE_AUTHORIZED=NO

GCP_BILLING_SPEND_AUTHORIZED=NO
AWS_BILLING_SPEND_AUTHORIZED=NO
CLOUD_RESOURCE_CREATION=FORBIDDEN
```

The user does not authorise personal cloud charges, payment-method registration or billing-account linkage.

## Academy-funded exception

An academy-owned account, academy-funded sandbox or separately granted free credit may be reassessed later only when all of the following are explicit:

- account owner and cost responsibility;
- services and quotas available;
- whether personal payment details are required;
- whether overage can charge the user;
- deployment duration;
- budget and notification controls;
- shutdown and deletion deadline;
- retained evidence lifecycle.

Such funding is not inferred and is not approved by this contract.

## Platform selection cost questions

Before AWS or another platform is selected, confirm:

| Question | Current state |
|---|---|
| Academy AWS services | `PENDING_CURRICULUM_CONFIRMATION` |
| RDS scope | `PENDING_CURRICULUM_CONFIRMATION` |
| Academy account or credits | `PENDING_CURRICULUM_CONFIRMATION` |
| Personal payment method required | `PENDING_CURRICULUM_CONFIRMATION` |
| Personal cost exposure | `PENDING_CURRICULUM_CONFIRMATION` |
| Deployment duration | `PENDING_CURRICULUM_CONFIRMATION` |
| Shutdown/deletion policy | `PENDING_CURRICULUM_CONFIRMATION` |

No missing answer currently blocks governance consistency because deployment implementation is deferred. Every answer becomes mandatory before resource creation.

## Teardown status

```text
TEARDOWN_REQUIRED_NOW=NO
TEARDOWN_STATUS=DEFERRED_UNTIL_PLATFORM_SELECTION
RETENTION_LOCK_AUTHORIZED=NO
```

There are no OP-3 cloud resources to tear down. A future implementation must define resource inventory, operator, independent reviewer, absolute deadline, deletion verification and any retained-evidence exception before provisioning.

## Reference-only GCP lifecycle

The previous Cloud Run, Workload Identity, Monitoring and Cloud Storage teardown model remains a reference for ordering and evidence design. It is not an allowlist of resources that may now be created.

## Acceptance boundary

This document authorises no project, account, billing linkage, API enablement, IAM mutation, image push, service deployment, bucket, monitoring resource, traffic change or retention lock.
