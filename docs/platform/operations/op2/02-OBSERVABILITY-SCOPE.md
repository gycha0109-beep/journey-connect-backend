# OP-2 Observability Scope

This phase implements repository-owned telemetry, dashboard and alert contracts, local safety tests, rollback evidence, approval packages and the OP-3 gate.

## Included

- SC-6 authoritative 27-metric continuity;
- seven OP-0 backlog metrics;
- bounded Micrometer counters, gauges and timers;
- 22-section dashboard definition;
- critical and warning rule definitions;
- kill-switch, cancellation, timeout and fallback verification;
- rollback Levels 1-7 matrix and honest drill status;
- exact-head CI evidence and independent verifier.

## Excluded

- real Stage 1 traffic;
- external metric scrape and retention proof;
- external dashboard deployment;
- alert receiver delivery acknowledgement;
- deployment, credential or network control-plane changes;
- manual 1% enablement and observation.
