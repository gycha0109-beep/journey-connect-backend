package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public record SearchCursorV1(
        SchemaVersion cursorVersion,
        RunRef searchRunId,
        SnapshotRef resultSnapshotRef,
        String queryFingerprint,
        String filterFingerprint,
        PolicyVersion sortPolicyVersion,
        PolicyVersion rankingPolicyVersion,
        Instant referenceTime,
        int nextRank,
        SearchOrderingTupleV1 lastOrderingTuple,
        SearchSurface surface,
        SearchEntityScope entityScope,
        SubjectRef subjectBindingRef,
        String sessionBindingRef,
        Instant issuedAt,
        Instant expiresAt,
        String checksum) {
    public SearchCursorV1 {
        SearchVersionValidatorV1.requireCursorVersion(cursorVersion);
        SearchChecks.requireNonNull(searchRunId, "searchRunId");
        SearchChecks.requireNonNull(resultSnapshotRef, "resultSnapshotRef");
        SearchChecks.requireFingerprint(queryFingerprint, "queryFingerprint");
        SearchChecks.requireFingerprint(filterFingerprint, "filterFingerprint");
        SearchVersionValidatorV1.requirePolicy(sortPolicyVersion, "sortPolicyVersion");
        SearchVersionValidatorV1.requirePolicy(rankingPolicyVersion, "rankingPolicyVersion");
        SearchChecks.requireInstant(referenceTime, "referenceTime");
        SearchChecks.requirePositive(nextRank, "nextRank");
        SearchChecks.requireNonNull(lastOrderingTuple, "lastOrderingTuple");
        SearchChecks.requireNonNull(surface, "surface");
        SearchChecks.requireNonNull(entityScope, "entityScope");
        if (sessionBindingRef != null) {
            sessionBindingRef = SearchChecks.requireOpaqueId(sessionBindingRef, "sessionBindingRef");
        }
        SearchChecks.requireOrdered(issuedAt, expiresAt, "issuedAt", "expiresAt");
        SearchChecks.requireFingerprint(checksum, "checksum");
        String expectedChecksum = SearchCursorChecksumV1.compute(cursorVersion, searchRunId, resultSnapshotRef,
                queryFingerprint, filterFingerprint, sortPolicyVersion, rankingPolicyVersion, referenceTime,
                nextRank, lastOrderingTuple, surface, entityScope, subjectBindingRef, sessionBindingRef,
                issuedAt, expiresAt);
        if (!checksum.equals(expectedChecksum)) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_CURSOR_INVALID,
                    "cursor checksum mismatch; checksum is structural integrity only, not production tamper protection");
        }
    }
}
