# Circuit-breaker Continuity

Existing lane-scoped circuit breakers remain authoritative. Timeout and late discard are counted as timeout-class failures; exceptions and cancellation are non-timeout failures. A breaker open decision blocks shadow work only.

OP-2 does not change breaker thresholds or restart behavior. Global or lane kill takes precedence over candidate invocation. Primary authority and response remain unaffected.

The RCA-2 protected regression workflow reruns existing breaker, timeout, executor and orchestrator tests at the PR exact head.
