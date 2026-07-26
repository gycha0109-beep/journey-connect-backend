# Warning Alert Inventory

SC-6 ceilings are unchanged:

- timeout rate: `20%`;
- exception rate: `25%`;
- queue rejection rate: `5%`;
- late discard rate: `5%`.

Warnings also cover task age, checkpoint lag, executor saturation, credential refresh failure, allowlist lookup failure and unexpected P1/P2 mismatch.

No numeric task-age or checkpoint-lag threshold is invented without empirical Stage 1 data and separate System Coordination approval. Those rules use explicit degraded signals until approved baselines exist.
