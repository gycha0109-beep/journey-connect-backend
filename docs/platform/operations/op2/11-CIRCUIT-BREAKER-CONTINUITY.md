# Circuit-breaker Continuity

The existing per-lane breaker remains authoritative. OPEN blocks invocation, HALF_OPEN is bounded, failures include timeout and late discard, and success closes only an approved probe.

No breaker state enables traffic automatically or transfers result authority.
