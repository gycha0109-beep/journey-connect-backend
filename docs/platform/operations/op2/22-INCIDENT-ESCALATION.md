# Incident Escalation

Critical signal sequence:

1. apply local `FLAG_OFF`, lane kill or global shadow disable;
2. preserve the primary result and stop shadow candidate invocation;
3. cancel queued work and discard late results;
4. request credential or network revoke when the external path exists;
5. notify Operations and the owning role;
6. retain bounded metric/audit evidence and exact deployment version;
7. keep OP-3 and Stage 1 blocked until recovery criteria and reapprovals are complete.

Escalation owners: Operations executes; Reliability handles runtime quality; Data handles write/checkpoint/lineage; Privacy/Security handles identity/credential/network; Intelligence handles mismatch semantics; System Coordination controls authority and re-entry.
