# Critical Alert Inventory

All critical conditions have tolerance `0` and are defined in both machine-readable JSON and Prometheus-compatible rules.

Required alerts cover response mutation, DB write, cache write, event emission, notification emission, redaction failure, production route, production identity, authority mismatch, traffic ceiling and credential scope violation.

Each alert requests one or more of `FLAG_OFF`, `LANE_KILL_SWITCH`, `GLOBAL_SHADOW_DISABLE`, `CREDENTIAL_REVOKE` or `NETWORK_ROUTE_REVOKE`. When an external route is unavailable, the application alert policy still applies a local fail-closed kill action and blocks enablement; it does not claim delivery.
