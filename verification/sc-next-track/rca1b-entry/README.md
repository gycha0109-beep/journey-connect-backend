# SC-4 RCA-1B Entry Verification

Governance-only evidence for `SC-4 RCA-1B Non-production Read-only Reconciliation Entry Authorization & Execution Boundary`.

Run:

```bash
python verification/sc-next-track/rca1b-entry/run_sc_rca1b_entry_verification.py
```

The verifier does not execute PostgreSQL, create a role/grant, run reconciliation queries, access production, or resolve actual identity mappings.