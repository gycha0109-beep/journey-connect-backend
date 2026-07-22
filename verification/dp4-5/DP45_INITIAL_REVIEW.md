# DP-4.5 Initial Self Review

- SQL allocation is exactly 35..37.
- SQL 01..34 is unchanged; SQL 38+ is absent.
- Persistence direction remains Recommendation P0 source to Data shadow candidate.
- One transaction produces a run plus exactly one mapped output or failure.
- Logical identity uses advisory locking plus a unique constraint.
- Duplicate returns existing evidence and creates no output.
- Conflict appends bounded evidence and creates no output.
- Output fingerprint is supplied by DP-4 and is not recomputed under another contract.
- Writer, reader and function-owner capabilities are separated.
- Production worker, scheduler, Recommendation write, replay, backfill and purge remain absent.

This note is temporary review evidence and may be folded into the final implementation document after CI.
