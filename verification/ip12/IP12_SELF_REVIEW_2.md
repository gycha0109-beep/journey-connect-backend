# IP-12 Self Review 2 — Failure Isolation and Operations

- Found: 3
- Fixed: 3
- Held: 0

1. A 20 ms non-blocking assertion was scheduler-sensitive; it now uses a still-bounded 100 ms ceiling.
2. Empty-cohort drill reused a killed switch and observed the wrong gate; switch state is isolated per scenario.
3. Disabled identity zero-call was implicit; direct and JUnit tests now assert identity resolver and work invocation remain zero.

Reverification: timeout, empty cohort, killed path, actual Search Runtime dispatch and legacy identity PASS.
