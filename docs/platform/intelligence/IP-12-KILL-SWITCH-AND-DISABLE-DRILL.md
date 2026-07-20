# IP-12 Kill-switch and Disable Drill

## Control order

1. `enabled`
2. global kill-switch
3. effective sampling
4. non-empty allowlist
5. authenticated account hash
6. cohort membership
7. deterministic sample
8. bounded executor

The switch is property/restart based. Dynamic reload and a remote platform are not claimed.

## Directly executed drill

- disabled defaults prevent identity resolution and dispatch
- enabled fixture with a deterministic 10 BPS account hash submits actual Search Runtime work
- legacy response identity remains unchanged
- activating the mutable drill switch blocks all subsequent work
- empty cohort dispatches zero
- runtime timeout interrupts work without joining the request thread

Spring production-profile restart execution is pending because the Gradle distribution is unavailable in this environment.
