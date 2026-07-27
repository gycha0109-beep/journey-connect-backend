# SC OP-3 Implementation Prompt

Execute OP-3 only after independently verifying the current GitHub remote state and confirming every condition in `58-SC-OP3-ENTRY-AND-GATE.md` is true.

Do not trust expected SHAs or status text without remote verification. Create a dedicated branch from the then-current `main`. Keep the PR Draft until all exact-head workflows and evidence artifacts pass.

The implementation must preserve:

- `CURRENT_P1_P2_ONLY` primary authority
- Candidate serving forbidden
- Effective traffic 0% until the approved manual execution step
- Production traffic 0%
- No production route, identity, database, or endpoint
- No automatic rollout
- Immediate kill switch and rollback authority

If any external dependency or approval is absent, stop with `OP3_ENTRY_BLOCKED` and do not simulate success. Do not mark Ready or merge without explicit SC approval.