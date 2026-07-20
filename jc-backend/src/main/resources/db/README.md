# Database migration locations

- `migration-legacy/`: pre-canonical backend schema. It is retained only for history and must not be enabled.
- `migration-v1_8/`: reviewed recommendation V7/V8 copies from the P0-1 package. It is not an automatic application location.
- Canonical runtime baseline: `database/journey-connect-db-v2.5/01..22` at repository root.

The backend uses `hibernate.ddl-auto=validate` and does not mutate the canonical schema.
Flyway stays disabled until the canonical 01..14 package is consolidated into a single owned migration history.
