package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jc.backend.common.DomainException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchExposureValidatorTest {

    private static final String SECRET =
            "search-exposure-test-secret-with-at-least-thirty-two-bytes";
    private static final String QUERY_FINGERPRINT = "a".repeat(64);
    private static final String SNAPSHOT_FINGERPRINT = "b".repeat(64);
    private static final Instant ISSUED_AT = Instant.parse("2026-08-04T06:00:00Z");

    private final SearchContextCodec codec = new SearchContextCodec(SECRET, 900);
    private final SearchExposureValidator validator = new SearchExposureValidator(codec);
    private final SearchExposureValidationPolicy policy =
            SearchExposureValidationPolicy.candidateV1();

    @Test
    void validBatchBindsActorContextItemsAndPagePositions() {
        SearchExposureCommand command = validator.validate(
                actor(41L),
                request(
                        SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                        List.of(
                                item("search-exp-2", "search-idem-2", 12L, 2, 2, 7_000, 1_800L,
                                        ISSUED_AT.plusSeconds(3)),
                                item("search-exp-1", "search-idem-1", 11L, 1, 1, 6_000, 1_500L,
                                        ISSUED_AT.plusSeconds(2)))),
                ISSUED_AT.plusSeconds(4),
                policy);

        assertEquals(SearchExposureContract.SCHEMA_VERSION, command.schemaVersion());
        assertEquals("subject:01J0SEARCHSUBJECT", command.subjectRef());
        assertEquals(SearchExposureContract.IDENTITY_SCHEME, command.identityScheme());
        assertEquals("search:run-1", command.searchRunId());
        assertEquals(QUERY_FINGERPRINT, command.queryFingerprint());
        assertEquals(SNAPSHOT_FINGERPRINT, command.resultSnapshotRef());
        assertEquals(List.of(1, 2), command.items().stream()
                .map(SearchExposureCommand.Item::pagePosition)
                .toList());
    }

    @Test
    void rejectsWrongUserAndResultBinding() {
        assertCode(
                () -> validator.validate(
                        actor(42L),
                        validRequest(),
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_CONTEXT_INVALID");

        SearchExposureDtos.BatchRequest wrongPosition = request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(item(
                        "search-exp-1",
                        "search-idem-1",
                        11L,
                        1,
                        2,
                        6_000,
                        1_500L,
                        ISSUED_AT.plusSeconds(2))));
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        wrongPosition,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_BINDING_INVALID");
    }

    @Test
    void rejectsUnsupportedVisibilityEvidence() {
        SearchExposureDtos.BatchRequest unsupportedRule = request(
                "search-item-visible-v2",
                validRequest().items());
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        unsupportedRule,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_RULE_UNSUPPORTED");

        SearchExposureDtos.BatchRequest insufficientRatio = request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(item(
                        "search-exp-1",
                        "search-idem-1",
                        11L,
                        1,
                        1,
                        4_999,
                        1_500L,
                        ISSUED_AT.plusSeconds(2))));
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        insufficientRatio,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_RULE_UNSUPPORTED");

        SearchExposureDtos.BatchRequest insufficientDwell = request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(item(
                        "search-exp-1",
                        "search-idem-1",
                        11L,
                        1,
                        1,
                        6_000,
                        999L,
                        ISSUED_AT.plusSeconds(2))));
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        insufficientDwell,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_RULE_UNSUPPORTED");
    }

    @Test
    void rejectsDuplicateOccurrenceIdentityAndInvalidTime() {
        SearchExposureDtos.BatchRequest duplicate = request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(
                        item("search-exp-1", "search-idem-1", 11L, 1, 1, 6_000, 1_500L,
                                ISSUED_AT.plusSeconds(2)),
                        item("search-exp-1", "search-idem-2", 12L, 2, 2, 7_000, 1_800L,
                                ISSUED_AT.plusSeconds(3))));
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        duplicate,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_BATCH_INVALID");

        SearchExposureDtos.BatchRequest tooEarly = request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(item(
                        "search-exp-1",
                        "search-idem-1",
                        11L,
                        1,
                        1,
                        6_000,
                        1_500L,
                        ISSUED_AT.minusSeconds(6))));
        assertCode(
                () -> validator.validate(
                        actor(41L),
                        tooEarly,
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_TIME_INVALID");
    }

    @Test
    void rejectsNumericOrUnapprovedIdentityFallback() {
        SearchExposureActor numericFallback =
                new SearchExposureActor(41L, "41", "legacy_user_numeric_v1", "search-jwt:session-1");

        assertCode(
                () -> validator.validate(
                        numericFallback,
                        validRequest(),
                        ISSUED_AT.plusSeconds(4),
                        policy),
                "SEARCH_EXPOSURE_ACTOR_INVALID");
    }

    private SearchExposureDtos.BatchRequest validRequest() {
        return request(
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                List.of(
                        item("search-exp-1", "search-idem-1", 11L, 1, 1, 6_000, 1_500L,
                                ISSUED_AT.plusSeconds(2)),
                        item("search-exp-2", "search-idem-2", 12L, 2, 2, 7_000, 1_800L,
                                ISSUED_AT.plusSeconds(3))));
    }

    private SearchExposureDtos.BatchRequest request(
            String ruleVersion,
            List<SearchExposureDtos.ItemRequest> items) {
        return new SearchExposureDtos.BatchRequest(
                "search-page:occ-1",
                resultContextToken(),
                ruleVersion,
                "web-20260804.1",
                items);
    }

    private SearchExposureDtos.ItemRequest item(
            String exposureId,
            String idempotencyKey,
            long postId,
            int absoluteRank,
            int pagePosition,
            int visibleRatioBasisPoints,
            long dwellMilliseconds,
            Instant exposedAt) {
        return new SearchExposureDtos.ItemRequest(
                exposureId,
                idempotencyKey,
                postId,
                absoluteRank,
                pagePosition,
                visibleRatioBasisPoints,
                dwellMilliseconds,
                exposedAt);
    }

    private SearchExposureActor actor(long userId) {
        return new SearchExposureActor(
                userId,
                "subject:01J0SEARCHSUBJECT",
                SearchExposureContract.IDENTITY_SCHEME,
                "search-jwt:session-1");
    }

    private String resultContextToken() {
        String snapshotToken = codec.encodeSnapshot(
                "search:run-1",
                41L,
                QUERY_FINGERPRINT,
                ISSUED_AT,
                20,
                SNAPSHOT_FINGERPRINT,
                SearchRankingPolicy.POLICY_VERSION,
                ISSUED_AT);
        SearchContextCodec.SnapshotContext snapshot = codec.decodeSnapshot(
                snapshotToken,
                41L,
                QUERY_FINGERPRINT,
                20,
                ISSUED_AT.plusSeconds(1));
        return codec.encodeResultContext(
                snapshot,
                List.of(
                        new SearchContextCodec.ResultBinding(11L, 1),
                        new SearchContextCodec.ResultBinding(12L, 2)),
                ISSUED_AT.plusSeconds(1));
    }

    private void assertCode(Runnable action, String code) {
        DomainException exception = assertThrows(DomainException.class, action::run);
        assertEquals(code, exception.getCode());
    }
}
