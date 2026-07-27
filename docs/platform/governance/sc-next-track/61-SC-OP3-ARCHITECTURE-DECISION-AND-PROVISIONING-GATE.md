# SC OP-3 Deployment Platform Governance and Provisioning Gate

## Current decision

```text
OP3_GOVERNANCE_CONSISTENT=YES

FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
DEPLOYMENT_PLATFORM_SELECTION_REQUIRED=YES
DEPLOYMENT_IMPLEMENTATION=DEFERRED

PLATFORM_ARCHITECTURE_REFERENCE=GCP_CLOUD_RUN
PLATFORM_ARCHITECTURE_REFERENCE_STATUS=DESIGN_ONLY
GCP_ARCHITECTURE_STATUS=REFERENCE_ONLY
GCP_PROVISIONING_PLANNED=NO

EXPECTED_TRAINING_DEPLOYMENT_PLATFORM=AWS
AWS_DEPLOYMENT_DECISION_STATUS=PENDING_CURRICULUM_CONFIRMATION

CLOUD_PROVISIONING_REQUIRED_NOW=NO
CLOUD_PROVISIONING_STATUS=DEFERRED_PLATFORM_UNDECIDED
OP3_CLOUD_PROVISIONING=DEFERRED_PLATFORM_UNDECIDED
```

This decision corrects the role of the GCP Cloud Run package merged through PR #45. It is retained as a reference architecture, not as the selected deployment target. AWS is an expected academy candidate only; it is not selected by this decision.

## Cost and mutation boundary

```text
PERSONAL_CLOUD_SPEND_ALLOWED=NO
PAID_CLOUD_USAGE=FORBIDDEN
COST_CEILING=0
BILLING_ACCOUNT_LINKAGE_AUTHORIZED=NO

GCP_RESOURCE_CREATION_AUTHORIZED=NO
GCP_BILLING_SPEND_AUTHORIZED=NO
GCP_IAM_MUTATION_AUTHORIZED=NO

AWS_RESOURCE_CREATION_AUTHORIZED=NO
AWS_BILLING_SPEND_AUTHORIZED=NO
AWS_IAM_MUTATION_AUTHORIZED=NO

CLOUD_RESOURCE_CREATION=FORBIDDEN
RETENTION_LOCK_AUTHORIZED=NO
```

No personal billing method, paid cloud account, project, service, bucket, monitoring resource or IAM trust may be created or changed under this governance state. Academy-funded accounts or separately granted free credits require a later explicit contract; they are not automatically approved.

## Preserved OP-3 safety state

```text
SC_OP3_EXECUTION_APPROVED=NO
OP3_ENTRY=BLOCKED
STAGE1_ENABLEMENT=BLOCKED
FEATURE_FLAG=OFF
NONPRODUCTION_TRAFFIC=0
EFFECTIVE_NONPRODUCTION_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
CANDIDATE_SERVING=FORBIDDEN
AUTOMATIC_ROLLOUT=FORBIDDEN
AUTHORITY_TRANSFER=FORBIDDEN
```

This governance correction does not execute OP-3, connect a candidate endpoint, enable traffic or close work orders #37-#43.

## GCP reference architecture

The existing GCP design remains useful only as a portable control model for:

- immutable revision identity;
- a zero-traffic candidate deployment concept;
- private invocation and least-privilege IAM separation;
- default-deny test-identity allowlisting;
- observability, audit and evidence envelopes;
- rollback and drill design;
- separation of execution writer and independent reviewer;
- later translation to the actually selected platform.

GCP-specific project IDs, regions, Cloud Run services, Workload Identity resources, buckets, dashboards and alert policies are not current provisioning inputs. They are `NOT_APPLICABLE` while GCP is reference-only.

## Expected academy AWS candidate

```text
EXPECTED_TRAINING_DEPLOYMENT_PLATFORM=AWS
AWS_DEPLOYMENT_DECISION_STATUS=PENDING_CURRICULUM_CONFIRMATION
```

Before selecting an AWS implementation, SC must confirm:

1. the academy-provided service scope, including whether EC2, ECS, Fargate, Elastic Beanstalk or another service is used;
2. whether RDS is in scope;
3. whether an academy account or practice credits are provided;
4. whether personal payment-method registration is required;
5. whether any personal cost can occur;
6. deployment duration;
7. shutdown and resource-deletion policy.

Until those facts are confirmed, no AWS architecture, account, IAM model, resource name or provisioning branch is authorised.

## Required-input classification

The authoritative matrix is:

`verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json`

Current governance consistency does not require GCP identifiers, cloud actors, an evidence bucket or an independent approver. Those inputs are deferred until a deployment platform is selected and an actual mutation, drill or acceptance activity is separately authorised.

```text
INDEPENDENT_APPROVER_REQUIRED_NOW=NO
INDEPENDENT_APPROVER_STATUS=DEFERRED_UNTIL_EXECUTION
```

The independent approver role remains defined and becomes mandatory before actual cloud mutation, traffic drill or operational acceptance.

## Re-entry gate

Provisioning may be reconsidered only after:

- final platform selection;
- academy curriculum and account/cost responsibility confirmation;
- platform-specific required inputs;
- explicit resource-creation, billing and IAM authorisations;
- a non-zero personal-spend exception only when separately approved;
- exact teardown and evidence-retention responsibilities.

No such gate is satisfied by this document.
