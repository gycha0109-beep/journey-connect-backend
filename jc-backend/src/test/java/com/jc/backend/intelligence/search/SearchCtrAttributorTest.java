package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jc.backend.intelligence.search.SearchCtrModels.BridgedClickOccurrence;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationInput;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationResult;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationWindow;
import com.jc.backend.intelligence.search.SearchCtrModels.ExposureOccurrence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCtrAttributorTest {

    private static final Instant WINDOW_START = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant COMPUTED_AT = Instant.parse("2026-08-05T02:00:00Z");
    private final SearchCtrAttributor attributor = new SearchCtrAttributor();

    @Test
    void oneClickSelectsMostRecentEligibleExposure() {
        ExposureOccurrence older = exposure("exp-older", "2026-08-05T00:05:00Z", "2026-08-05T00:05:01Z");
        ExposureOccurrence newer = exposure("exp-newer", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");

        EvaluationResult result = evaluate(
                List.of(older, newer),
                List.of(click("click-1", "2026-08-05T00:11:00Z")));

        assertEquals(2, result.eligibleExposureCount());
        assertEquals(1, result.attributedExposureCount());
        assertEquals(5_000, result.ctrBasisPoints());
        assertEquals("exp-newer", result.attributions().getFirst().exposureId());
    }

    @Test
    void lowerBoundIsInclusiveAndThirtyMinuteUpperBoundIsExclusive() {
        ExposureOccurrence exposure = exposure("exp-boundary", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");

        EvaluationResult result = evaluate(
                List.of(exposure),
                List.of(
                        click("click-lower", "2026-08-05T00:10:00Z"),
                        click("click-upper", "2026-08-05T00:40:00Z")));

        assertEquals(1, result.attributedExposureCount());
        assertEquals(10_000, result.ctrBasisPoints());
        assertEquals(List.of("click-lower"), result.attributions().stream()
                .map(SearchCtrModels.Attribution::clickEventId).toList());
        assertEquals(List.of("click-upper"), result.unattributedClickEventIds());
    }

    @Test
    void multipleClicksIncreaseExposureNumeratorAtMostOnce() {
        ExposureOccurrence exposure = exposure("exp-one", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");

        EvaluationResult result = evaluate(
                List.of(exposure),
                List.of(
                        click("click-1", "2026-08-05T00:11:00Z"),
                        click("click-2", "2026-08-05T00:12:00Z")));

        assertEquals(2, result.attributions().size());
        assertEquals(1, result.attributedExposureCount());
        assertEquals(List.of("exp-one"), result.attributedExposureIds());
    }

    @Test
    void sessionRunRankAndConsistencyMismatchesAreNotAttributed() {
        ExposureOccurrence exposure = exposure("exp-match", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");
        BridgedClickOccurrence mismatch = new BridgedClickOccurrence(
                "click-mismatch", "subject:opaque-1", "session-other", "search:run-1",
                41L, 3, "a".repeat(64), "b".repeat(64), "search-ranking-v1",
                Instant.parse("2026-08-05T00:11:00Z"), Instant.parse("2026-08-05T00:11:01Z"));

        EvaluationResult result = evaluate(List.of(exposure), List.of(mismatch));

        assertEquals(0, result.attributedExposureCount());
        assertEquals(List.of("click-mismatch"), result.unattributedClickEventIds());
    }

    @Test
    void zeroDenominatorProducesNullCtrInsteadOfZeroPercent() {
        EvaluationResult result = evaluate(
                List.of(exposure("outside", "2026-08-04T23:59:59Z", "2026-08-05T00:00:00Z")),
                List.of());

        assertEquals(0, result.eligibleExposureCount());
        assertEquals(0, result.attributedExposureCount());
        assertNull(result.ctrBasisPoints());
        assertEquals(SearchCtrContract.PROVISIONAL_STATUS, result.status());
    }

    @Test
    void exactTieUsesExposureIdAscending() {
        ExposureOccurrence b = exposure("exp-b", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");
        ExposureOccurrence a = exposure("exp-a", "2026-08-05T00:10:00Z", "2026-08-05T00:10:01Z");

        EvaluationResult result = evaluate(
                List.of(b, a),
                List.of(click("click-tie", "2026-08-05T00:11:00Z")));

        assertEquals("exp-a", result.attributions().getFirst().exposureId());
    }

    private EvaluationResult evaluate(
            List<ExposureOccurrence> exposures,
            List<BridgedClickOccurrence> clicks) {
        return attributor.evaluate(new EvaluationInput(
                new EvaluationWindow(WINDOW_START, WINDOW_END),
                COMPUTED_AT,
                exposures,
                clicks));
    }

    private ExposureOccurrence exposure(String id, String exposedAt, String receivedAt) {
        return new ExposureOccurrence(
                id, "subject:opaque-1", "session-1", "search:run-1", 41L, 3,
                "a".repeat(64), "b".repeat(64), "search-ranking-v1",
                Instant.parse(exposedAt), Instant.parse(receivedAt));
    }

    private BridgedClickOccurrence click(String id, String occurredAt) {
        return new BridgedClickOccurrence(
                id, "subject:opaque-1", "session-1", "search:run-1", 41L, 3,
                "a".repeat(64), "b".repeat(64), "search-ranking-v1",
                Instant.parse(occurredAt), Instant.parse(occurredAt).plusSeconds(1));
    }
}
