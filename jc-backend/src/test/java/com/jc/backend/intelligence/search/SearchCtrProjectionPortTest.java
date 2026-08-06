package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SearchCtrProjectionPortTest {

    @Test
    void writerCommandContainsOnlyWindowConcurrencyAndProducerInputs() {
        Instant start = Instant.parse("2026-08-06T00:00:00Z");
        Instant end = Instant.parse("2026-08-06T01:00:00Z");
        SearchCtrProjectionPort.WriteCommand command = new SearchCtrProjectionPort.WriteCommand(
                start,
                end,
                "search-ctr-projection:0123456789abcdef0123456789abcdef",
                "search-ctr:2026-08-06T00",
                "search-ctr-writer-test-v1");

        SearchCtrProjectionPort port = value -> new SearchCtrProjectionPort.WriteResult(
                SearchCtrProjectionPort.WriteStatus.STORED,
                "search-ctr-projection:fedcba9876543210fedcba9876543210",
                "a".repeat(64),
                value.expectedPredecessorProjectionId(),
                SearchCtrContract.METRIC_ID,
                SearchCtrContract.METRIC_VERSION,
                value.windowStart(),
                value.windowEnd(),
                SearchCtrContract.PROVISIONAL_STATUS,
                2,
                1,
                5000,
                end.plusSeconds(1),
                end);

        SearchCtrProjectionPort.WriteResult result = port.write(command);

        assertEquals(SearchCtrProjectionPort.WriteStatus.STORED, result.status());
        assertEquals(command.expectedPredecessorProjectionId(), result.predecessorProjectionId());
        assertEquals(5000, result.ctrBasisPoints());
    }

    @Test
    void writerCommandRejectsInvalidWindowAndPredecessor() {
        Instant start = Instant.parse("2026-08-06T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new SearchCtrProjectionPort.WriteCommand(
                start, start, null, "valid-idempotency", "writer-v1"));
        assertThrows(IllegalArgumentException.class, () -> new SearchCtrProjectionPort.WriteCommand(
                start,
                start.plusSeconds(1),
                "subject:must-not-be-accepted",
                "valid-idempotency",
                "writer-v1"));
    }
}
