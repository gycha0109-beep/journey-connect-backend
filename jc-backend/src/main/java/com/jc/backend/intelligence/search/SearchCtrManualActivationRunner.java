package com.jc.backend.intelligence.search;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

public final class SearchCtrManualActivationRunner implements ApplicationRunner {

    private final SearchCtrManualActivationProperties properties;
    private final SearchCtrManualActivationGate gate;
    private final SearchCtrManualActivationPort port;
    private final Environment environment;
    private final Clock clock;
    private final boolean reliabilityCapabilityRequired;

    public SearchCtrManualActivationRunner(
            SearchCtrManualActivationProperties properties,
            SearchCtrManualActivationGate gate,
            SearchCtrManualActivationPort port,
            Environment environment,
            Clock clock,
            boolean reliabilityCapabilityRequired) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.port = Objects.requireNonNull(port, "port");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reliabilityCapabilityRequired = reliabilityCapabilityRequired;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        executeOnce();
    }

    public SearchCtrManualActivationPort.Result executeOnce() {
        Instant observedAt = clock.instant();
        SearchCtrManualActivationGate.ApprovedRun approved = gate.approve(
                properties,
                environment.getActiveProfiles(),
                observedAt,
                reliabilityCapabilityRequired);
        String operationId = "search-ctr-manual-run:"
                + UUID.randomUUID().toString().replace("-", "");

        SearchCtrManualActivationPort.Result result = port.execute(
                new SearchCtrManualActivationPort.Command(
                        operationId,
                        approved.window().start(),
                        approved.window().end(),
                        approved.environment(),
                        SearchCtrActivationPolicy.POLICY_VERSION,
                        approved.observedAt(),
                        approved.idempotencyKey(),
                        approved.producerBuildId()));

        return switch (result.writeStatus()) {
            case STORED, DUPLICATE -> result;
            case IDEMPOTENCY_CONFLICT -> throw new IllegalStateException(
                    "manual Search CTR execution stopped on idempotency conflict: "
                            + result.operationId());
            case PREDECESSOR_CONFLICT -> throw new IllegalStateException(
                    "manual Search CTR execution stopped on predecessor conflict; blind retry is forbidden: "
                            + result.operationId());
        };
    }
}
