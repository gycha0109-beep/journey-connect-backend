# SR-6F-I Search CTR Stage Readiness Handoff

This directory converts the external SR-6F-H resume prerequisites into a platform-neutral, machine-verifiable handoff.

It does not select a cloud provider, create infrastructure, read GitHub secrets, connect to a database, or mutate the authoritative SR-6F-H contract.

## Current state

```text
SR6FI_READINESS_STATUS=BLOCKED_PLATFORM_UNDECIDED
SR6FI_FINAL_DEPLOYMENT_PLATFORM=UNDECIDED
SR6FH_EXECUTION_STATUS=BLOCKED_EXTERNAL_STAGE_ACCESS
```

The authoritative platform source is:

```text
verification/sc-next-track/op3-entry/sc-op3-required-input-decision-matrix.json
```

The current matrix remains governance-only with platform selection and paid execution deferred.

## Files

```text
stage-readiness-manifest.env.example  blocked, secrets-free template
required-secret-names.txt             names-only inventory; never values
verify_stage_readiness.py             fail-closed template/ready verifier
render_sr6fh_binding.py               review-only binding proposal renderer
test_verify_stage_readiness.py        standard-library regression tests
```

## Verify the current blocked template

```bash
python3 operations/search-ctr/sr6fi/verify_stage_readiness.py \
  --manifest operations/search-ctr/sr6fi/stage-readiness-manifest.env.example \
  --mode template
```

This command succeeds only when unresolved values remain explicit and match the authoritative OP-3 matrix.

## Future ready verification

A future operator may copy the example outside version control, fill only non-secret identifiers and hashes, and run:

```bash
python3 operations/search-ctr/sr6fi/verify_stage_readiness.py \
  --manifest /secure/path/stage-readiness.env \
  --mode ready \
  --output /safe/path/stage-readiness-verification.json
```

Ready mode requires all of the following:

- authoritative final platform selection;
- deployment implementation no longer deferred;
- resource, billing, and IAM mutation authorization;
- exact deployed source SHA and artifact digest;
- reviewed DB package digest and deployment-evidence digest;
- endpoint SHA-256 only, never the endpoint string;
- GitHub `stage` environment protection verification;
- names-only secret inventory verification;
- execution, revoke, approval, incident, cost, and teardown actors;
- evidence store, retention, and teardown deadline;
- zero stage and production traffic;
- candidate serving forbidden;
- finality writes disabled.

## Render a binding proposal

After ready verification:

```bash
python3 operations/search-ctr/sr6fi/render_sr6fh_binding.py \
  --manifest /secure/path/stage-readiness.env \
  --output-dir /safe/path/sr6fh-binding
```

The output is `PROPOSED_NOT_AUTHORIZED`. It does not modify `operations/search-ctr/sr6fh/stage-execution-contract.env`. A separate reviewed commit and explicit authorization remain required.

## Prohibited content

The manifest must never contain:

- database or service endpoint strings;
- JDBC URLs;
- passwords, tokens, JWTs, private keys, or secret values;
- user, session, exposure, click, or query identity;
- inferred platform/resource assignments;
- production traffic or serving authority.
