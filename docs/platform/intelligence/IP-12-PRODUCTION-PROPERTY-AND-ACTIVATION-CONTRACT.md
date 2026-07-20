# IP-12 Production Property and Activation Contract

Prefix: `app.intelligence.search-shadow.production`.

| Property | Default | Approved bound | Failure behavior |
|---|---:|---:|---|
| `enabled` | false | boolean | false means dispatch 0 |
| `kill-switch` | true | boolean | missing/invalid binding cannot activate |
| `sampling-bps` | 0 | 0..10 | 11+ and negative reject startup |
| `max-approved-sampling-bps` | 10 | exactly 10 | any other value rejects startup |
| `allowlist-hashes` | empty | max 3 SHA-256 values | malformed/duplicate rejects startup |
| `max-candidates` | 100 | exactly 100 | reject |
| `timeout-ms` | 200 | exactly 200 | reject |
| `hard-timeout-ms` | 300 | exactly 300 | reject |
| `core-concurrency` | 1 | exactly 1 | reject |
| `max-concurrency` | 2 | exactly 2 | reject |
| `queue-capacity` | 8 | exactly 8 | reject |

Activation requires every condition: production profile, enabled, kill-switch inactive, sample 1..10, authenticated account hash, allowlist membership, deterministic sample acceptance, available bounded resource and valid projection input. Capability beans do not grant activation authority.
