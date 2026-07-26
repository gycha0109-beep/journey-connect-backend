# Evidence Retention

CI uploads the OP-2 runtime verifier output, machine contracts, dashboard/rule definitions and JUnit reports as `op2-rca2-stage1-observability-safety-evidence` with 90-day retention.

Runtime evidence records the exact tested SHA, work-start SHA, source OP-1 heads, check results, external blockers and aggregate SHA-256 digest of contract files.

No raw identity, token, full endpoint URL, canonical payload or unbounded exception text is retained. Incident handoff references hashes and bounded classifications only.
