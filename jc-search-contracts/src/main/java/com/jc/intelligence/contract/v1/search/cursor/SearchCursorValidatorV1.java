package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import java.time.Instant;
import java.util.Objects;

public final class SearchCursorValidatorV1 {
    private SearchCursorValidatorV1() { }

    public static void validateChecksum(SearchCursorV1 cursor) {
        SearchChecks.requireNonNull(cursor, "cursor");
        if (!SearchCursorChecksumV1.matches(cursor)) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_INVALID,
                    "cursor checksum mismatch; contract checksum is not production tamper protection");
        }
    }

    public static void validateExpiration(SearchCursorV1 cursor, Instant validationTime) {
        validateChecksum(cursor);
        SearchChecks.requireInstant(validationTime, "validationTime");
        if (validationTime.isAfter(cursor.expiresAt())) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_EXPIRED, "cursor expired");
        }
    }

    public static void validateBinding(
            SearchCursorV1 cursor,
            String expectedQueryFingerprint,
            String expectedFilterFingerprint,
            PolicyVersion expectedRankingPolicyVersion,
            SearchSurface expectedSurface,
            SearchEntityScope expectedScope,
            SubjectRef expectedSubject,
            String expectedSession,
            Instant expectedReferenceTime) {
        validateChecksum(cursor);
        SearchChecks.requireInstant(expectedReferenceTime, "expectedReferenceTime");
        if (!cursor.queryFingerprint().equals(expectedQueryFingerprint)
                || !cursor.filterFingerprint().equals(expectedFilterFingerprint)
                || !cursor.rankingPolicyVersion().equals(expectedRankingPolicyVersion)
                || cursor.surface() != expectedSurface
                || cursor.entityScope() != expectedScope
                || !Objects.equals(cursor.subjectBindingRef(), expectedSubject)
                || !Objects.equals(cursor.sessionBindingRef(), expectedSession)
                || !cursor.referenceTime().equals(expectedReferenceTime)) {
            throw SearchChecks.invalid(SearchValidationErrorCode.SEARCH_CURSOR_MISMATCH,
                    "cursor binding mismatch");
        }
    }

}
