package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchExposurePersistencePortTest {

    @Test
    void disabledPortNeverReportsStoredRows() {
        SearchExposurePersistencePort port =
                SearchExposurePersistencePort.disabledPendingApproval();
        SearchExposureCanonicalizer.CanonicalBatch batch =
                new SearchExposureCanonicalizer.CanonicalBatch(
                        SearchExposureContract.SCHEMA_VERSION,
                        "a".repeat(64),
                        "{}".getBytes(StandardCharsets.UTF_8),
                        "{}",
                        List.of());

        SearchExposurePersistencePort.StoreBatchResult result = port.store(batch);

        assertEquals(
                SearchExposurePersistencePort.Status.DISABLED_PENDING_APPROVAL,
                result.status());
        assertEquals(0, result.storedCount());
        assertEquals(0, result.duplicateCount());
    }
}
