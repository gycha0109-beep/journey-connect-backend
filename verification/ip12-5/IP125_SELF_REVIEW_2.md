# IP-12.5 Self Review 2 — Failure and Operations

- Missing/malformed activation inputs fail startup when positive activation is requested.
- Closed window blocks before identity resolution or executor submission.
- Kill-switch, disabled configuration and zero sampling retain precedence.
- Metric/log failures remain isolated.
- Finding: prior runtime did not bind approval, execution, rollback, window or metric path to dispatch. Fixed.
