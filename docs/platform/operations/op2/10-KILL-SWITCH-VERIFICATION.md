# Kill-switch Verification

Application continuity is verified for feature flag OFF, lane kill, global shadow disable, candidate invocation blocking, timeout, cancellation, late discard, primary fallback retention and restart-safe defaults.

Kill controls affect the shadow lane only and must not interrupt or mutate the primary result.
