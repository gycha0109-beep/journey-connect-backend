# IP-12 Internal Account Allowlist Implementation

The production allowlist accepts at most three lowercase SHA-256 account hashes. Whitespace is trimmed, uppercase hex is normalized, duplicates and malformed values are rejected, and an empty list is safe.

The authenticated JWT subject is accepted only when it is a positive numeric account identifier. It is converted in memory to `SHA-256("user:" + subject)`, checked for membership and then discarded. The raw subject and hash are not logged, persisted or used as metric tags.

Current production resources contain no fixture and no account hash. Actual cohort remains `empty / 0%`.
