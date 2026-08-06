package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SearchCtrManualActivationRunnerTest {

    @Test
    void oneShotRunnerExecutesExactlyOneApprovedUtcWindow() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SearchCtrManualActivationPort.Command> captured = new AtomicReference<>();
        SearchCtrManualActivationPort port = command -> {
            calls.incrementAndGet();
            captured.set(command);
            return result(command, SearchCtrProjectionPort.WriteStatus.STORED);
        };

        SearchCtrManualActivationPort.Result result = runner(port).executeOnce();

        assertEquals(SearchCtrProjectionPort.WriteStatus.STORED, result.writeStatus());
        assertEquals(1, calls.get());
        assertEquals(Instant.parse("2026-08-06T00:00:00Z"), captured.get().windowStart());
        assertEquals(Instant.parse("2026-08-06T01:00:00Z"), captured.get().windowEnd());
        assertTrue(captured.get().operationId().matches("^search-ctr-manual-run:[0-9a-f]{32}$"));
        assertTrue(captured.get().idempotencyKey().contains("sr6ff-runner-test-v1"));
    }

    @Test
    void duplicateIsAcceptedWithoutSecondExecution() {
        AtomicInteger calls = new AtomicInteger();
        SearchCtrManualActivationPort port = command -> {
            calls.incrementAndGet();
            return result(command, SearchCtrProjectionPort.WriteStatus.DUPLICATE);
        };

        SearchCtrManualActivationPort.Result result = runner(port).executeOnce();

        assertEquals(SearchCtrProjectionPort.WriteStatus.DUPLICATE, result.writeStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void writerConflictsStopAfterOneAttempt() {
        for (SearchCtrProjectionPort.WriteStatus blocked : new SearchCtrProjectionPort.WriteStatus[] {
                SearchCtrProjectionPort.WriteStatus.IDEMPOTENCY_CONFLICT,
                SearchCtrProjectionPort.WriteStatus.PREDECESSOR_CONFLICT
        }) {
            AtomicInteger calls = new AtomicInteger();
            SearchCtrManualActivationPort port = command -> {
                calls.incrementAndGet();
                return result(command, blocked);
            };

            assertThrows(IllegalStateException.class, () -> runner(port).executeOnce());
            assertEquals(1, calls.get());
        }
    }

    private static SearchCtrManualActivationRunner runner(SearchCtrManualActivationPort port) {
        SearchCtrManualActivationProperties properties = new SearchCtrManualActivationProperties();
        properties.setEnabled(true);
        properties.setKillSwitch(false);
        properties.setEnvironment("stage");
        properties.setWindowStart("2026-08-06T00:00:00Z");
        properties.setProducerBuildId("sr6ff-runner-test-v1");
        properties.setApprovalRef("approval:sr6ff-runner-test-v1");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("stage");
        return new SearchCtrManualActivationRunner(
                properties,
                new SearchCtrManualActivationGate(
                        SearchCtrActivationPolicy.RuntimeMode.NONPRODUCTION_MANUAL),
                port,
                environment,
                Clock.fixed(Instant.parse("2026-08-06T02:00:00Z"), ZoneOffset.UTC),
                true);
    }

    private static SearchCtrManualActivationPort.Result result(
            SearchCtrManualActivationPort.Command command,
            SearchCtrProjectionPort.WriteStatus status) {
        return new SearchCtrManualActivationPort.Result(
                command.operationId(),
                status,
                "search-ctr-projection:11111111111111111111111111111111",
                "a".repeat(64),
                null,
                SearchCtrContract.METRIC_ID,
                SearchCtrContract.METRIC_VERSION,
                command.windowStart(),
                command.windowEnd(),
                SearchCtrContract.PROVISIONAL_STATUS,
                0,
                0,
                null,
                command.observedAt(),
                null);
    }
}
