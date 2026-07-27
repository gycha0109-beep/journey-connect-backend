# SC OP-3 Evidence Acceptance Contract

## Rule

Actual OP-3 work-order acceptance requires attributable, reproducible, environment-bound and retained evidence. Governance-only documentation is not external execution evidence.

## Current timing

```text
CLOUD_PROVISIONING_REQUIRED_NOW=NO
DEPLOYMENT_IMPLEMENTATION=DEFERRED
INDEPENDENT_APPROVER_REQUIRED_NOW=NO
INDEPENDENT_APPROVER_STATUS=DEFERRED_UNTIL_EXECUTION
CURRENT_ACCEPTANCE_RESULT=NOT_READY
```

An independent approver is not required to edit governance contracts when no cloud mutation, drill, deployment or traffic event occurs. The approver becomes mandatory before actual execution evidence can be accepted.

## Mandatory future evidence envelope

Every executed work order must provide:

1. work-order issue number;
2. accountable role, executing actor and independent reviewer;
3. exact repository revision;
4. selected non-production environment and resource identity;
5. start and completion timestamps;
6. commands or procedures;
7. expected and actual results;
8. immutable audit and artefact references;
9. digest and protected-data review;
10. acceptance status and timestamp.

## Platform-neutral requirement

The authoritative store and audit references must be selected with the final deployment platform. GCP Cloud Storage is reference-only; AWS or another platform is not selected.

GitHub Actions Artifacts v4 may remain an intermediate transport, but it does not by itself satisfy authoritative retention.

## Automatic rejection conditions

Evidence is rejected when it:

- uses only templates, mocks or localhost;
- omits environment, revision, actor, timestamp or immutable reference;
- exposes a raw credential, token or personal identity;
- uses production access;
- reports a placeholder as deployed;
- serves candidate output or changes primary authority;
- performs an unauthorised resource, billing, IAM or retention-lock mutation.

## Gate impact

Open work orders keep `OP3_ENTRY=BLOCKED`. All accepted work orders permit reassessment only; a separate SC execution decision remains required.
