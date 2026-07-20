# IP-11.9 Account Allowlist Contract

- contract ID: `internal-production-shadow-allowlist-v1`
- input: lowercase SHA-256 account key only
- roles: `PROJECT_OWNER`, `TEAM_LEAD`, `BACKEND_OWNER`
- maximum entries: 3
- duplicate hashes prohibited
- pending state requires an empty list
- actual account hashes: none
- effective cohort: 0
- production dispatch: 0

IP-12 implements parser and membership matching but does not invent account values.
