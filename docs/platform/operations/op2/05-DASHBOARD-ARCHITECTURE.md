# Dashboard Architecture

The repository-owned dashboard contract is `ops/observability/rca2/op2/grafana-dashboard.json`.

It contains 22 sections: traffic selection, executor active, executor queue, task age, cancellation, latency, timeout, exception, queue rejection, late discard, circuit breaker, kill switch, P1 mismatch, P2 mismatch, checkpoint lag, lineage mismatch, identity blocked, redaction, response mutation, write/event violations, production route detection and authority mismatch.

Queries use bounded metric dimensions only. The dashboard does not expose raw identities, tokens, full endpoint URLs, request payloads or error messages.

This JSON is a reproducible application contract, not proof that a Grafana instance exists or has accepted it.
