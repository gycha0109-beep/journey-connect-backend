# OP-1 Continuity Verification

OP-2 preserves the OP-1 application-side environment and access boundary.

- endpoint policy remains non-production TLS-only and fail-closed;
- production-like routes, IP literals, redirects, userinfo, query and fragment remain blocked;
- credential abstraction remains short-lived and read-only;
- allowlist remains default-deny and raw identities are not stored or logged;
- stable hash cohort remains capped at 1%;
- configured and effective traffic remain 0%;
- candidate adapter remains contract-only and unresolved;
- primary response, DB/cache, event/notification and ranking-feedback boundaries remain unchanged.

External endpoint, credential, allowlist and candidate source remain unresolved. OP-2 does not fabricate any replacement.
