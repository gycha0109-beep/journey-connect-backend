# IP-11.9 External Attestation Correction

## Status

`EXTERNAL_ATTESTATION_COMPLETE_WITH_FIXES / OPERATIONAL_INPUTS_PENDING`

## Baseline evidence

- attested ZIP: `JC-IP-11-9-Attested-Final.zip`
- SHA-256: `379f93073d117bb22184e3ea72e371372b75b400ccb9b9fb1a013904c88acf14`
- final package audit: 1,988 files, CRC PASS, maximum internal path 181, re-extract static verification 64/64 PASS

The current execution environment did not expose the attested ZIP binary. IP-12 therefore reconstructed the baseline from the latest locally available full project plus the exact final attestation delta recorded in the supplied evidence. The reconstruction is explicit and is not represented as byte-for-byte extraction of the unavailable ZIP.

## Final attested fixes carried forward

1. `ProjectionVisibilityStatus.PRIVATE` fixture corrected to the actual fail-closed `NON_PUBLIC` enum.
2. IP-11.5 static test now obtains the projection schema from `SearchProductionContractIds` instead of duplicating a literal authority.
3. Queue saturation fixture now uses a deterministic started latch.
4. SQL 28 temporarily disables `posts_set_updated_at`, assigns `updated_at = clock_timestamp()` for transition checks, then re-enables the trigger.

Production main source, production resources, Gradle build logic and SQL 01..27 remained protected in IP-11.9. The only approved source delta was three tests and SQL 28 smoke-fixture correction.
