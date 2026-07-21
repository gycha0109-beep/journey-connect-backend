# IP-12.5 Self Review 1 — Architecture

- Controller remains free of activation logic.
- Production adapter reuses the IP-9 bridge.
- Operational references are properties/runtime metadata, not HTTP response authority.
- No SQL or persistence change.
- Finding: GitHub HEAD omitted both production resource files required by the static contract. Fixed with safe defaults.
