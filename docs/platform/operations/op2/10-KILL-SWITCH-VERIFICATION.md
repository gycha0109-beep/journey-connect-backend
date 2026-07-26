# Kill-switch Verification

Verified application controls:

- feature flag defaults to OFF and traffic 0;
- P1 and P2 lane kills are independent;
- global kill blocks all shadow submissions;
- candidate invocation remains blocked when source is unresolved;
- queued tasks can be cancelled and emit cancellation completion;
- in-flight timeout and late discard remain bounded;
- primary result remains the fallback and is never mutated;
- no automatic restart or ramp exists;
- kill state is exposed through bounded gauges;
- process configuration restores the safe killed/default-OFF state.

Kill controls affect only the shadow path. They do not stop production traffic or the primary P1/P2 result path.
