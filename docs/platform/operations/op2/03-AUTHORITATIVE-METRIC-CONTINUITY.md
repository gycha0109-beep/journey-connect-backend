# Authoritative Metric Continuity

The source of truth is SC-6 `SC6_METRIC_INVENTORY.tsv` and OP-0 metric backlog. All 27 names, types, owners, label boundaries and purposes are retained in `metric-inventory.json`.

No existing RCA-2 metric is renamed or removed. OP-2 adds a parallel canonical registry under `Rca2Op2Telemetry` and mirrors only semantically equivalent runtime observations.

P1 expected/protected gaps remain separate from unexpected mismatches. P2 migration gaps remain separate from unexpected mismatches. Zero-tolerance conditions remain zero.

Dynamic labels are restricted to bounded lane, stage, result, class, reason, gap class or mismatch class values. Raw identity, token, full endpoint URL and unbounded error text are rejected to `unknown`.
