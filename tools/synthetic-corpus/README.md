# Post-PF5 Synthetic Corpus Conformance Tooling

This directory contains a deterministic, tooling-only synthetic corpus generator and validator for post-PF5 conformance checks.

## Authority binding

The harness is rebound to the Crew contract present on `main` at baseline commit `11bb8d4c1c63cad07009f9334a8e20fa417deb0c`:

- `CrewMemberStatus`: `OWNER`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
- capacity-consuming membership statuses: `OWNER`, `APPROVED`
- non-capacity membership statuses: `PENDING`, `REJECTED`, `CANCELLED`
- Crew capacity range: `2..20`

The source authority is the production Crew implementation under `jc-backend/src/main/java/com/jc/backend/crew/`. The tooling copies only the minimum contract values necessary to detect semantic drift; it does not import, call, seed, or modify application runtime code.

## Boundary

This replacement is intentionally narrower than PR #79.

It does **not**:

- integrate with production runtime code;
- call application APIs;
- connect to or seed any database;
- create SQL or migrations;
- allocate or implement PF6;
- allocate SQL63 or later;
- model recommendation exposure, impression, ranking, or delivery semantics.

`PF6` and `SQL63+` remain `UNALLOCATED` and are asserted in generated metadata.

PR #79 is donor material only. Seeded determinism, synthetic identity, and graph-integrity mechanics are retained conceptually; stale Crew DTO, membership, recommendation, and exposure assumptions are not carried forward.

## Generate

```bash
python tools/synthetic-corpus/generate.py \
  --seed 20260827 \
  --users 64 \
  --crews 12 \
  --social-edges 120 \
  --output build/synthetic-corpus/corpus.json
```

Output is canonical UTF-8 JSON with sorted keys and compact separators, making equal seeds byte-deterministic.

## Validate an existing corpus

```bash
python tools/synthetic-corpus/generate.py \
  --validate-only build/synthetic-corpus/corpus.json
```

Validation covers:

- synthetic identity uniqueness and non-routable email boundary;
- exact Crew membership status domain;
- exactly one matching `OWNER` membership per Crew;
- capacity computed from `OWNER + APPROVED` only;
- `PENDING` explicitly non-capacity-consuming;
- Crew/user referential integrity;
- canonical, unique, non-self social graph edges;
- PF6 / SQL63+ non-allocation metadata.

## Tests

```bash
python -m unittest discover -s tools/synthetic-corpus/tests -p 'test_*.py' -v
```

The tests include same-seed byte determinism, different-seed divergence, contract/domain assertions, explicit pending-capacity behavior, corruption rejection, graph integrity, and CLI generation/validation.
