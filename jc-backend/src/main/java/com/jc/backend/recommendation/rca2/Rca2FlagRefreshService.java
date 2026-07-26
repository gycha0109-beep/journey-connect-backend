package com.jc.backend.recommendation.rca2;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;

/** Refreshes a signed, environment-bound snapshot; every read or parse failure replaces it with OFF. */
public final class Rca2FlagRefreshService implements AutoCloseable {
    private final Environment environment;
    private final Rca2FeatureFlagPolicy policy;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    public Rca2FlagRefreshService(Environment environment, Rca2FeatureFlagPolicy policy, Clock clock) {
        this.environment = Objects.requireNonNull(environment);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory());
        refresh();
        scheduler.scheduleAtFixedRate(this::refresh, Rca2FeatureFlagPolicy.REFRESH_INTERVAL.toSeconds(),
                Rca2FeatureFlagPolicy.REFRESH_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    void refresh() {
        Instant now = clock.instant();
        try {
            String global = environment.getProperty("app.recommendation.rca2.flag");
            boolean p1 = strictBoolean("app.recommendation.rca2.lane.p1-enabled");
            boolean p2 = strictBoolean("app.recommendation.rca2.lane.p2-enabled");
            int traffic = strictInteger("app.recommendation.rca2.traffic-percent");
            boolean signature = strictBoolean("app.recommendation.rca2.config-signature-verified");
            String environmentValue = environment.getProperty("app.recommendation.rca2.environment", "missing");
            String version = environment.getProperty("app.recommendation.rca2.flag-version", "missing");
            policy.replace(new Rca2FeatureFlagPolicy.Snapshot(global, p1, p2, traffic, environmentValue,
                    version, now, now, now.plus(Rca2FeatureFlagPolicy.MAX_TTL), signature));
        } catch (RuntimeException exception) {
            policy.replace(new Rca2FeatureFlagPolicy.Snapshot("malformed", false, false, -1, "invalid",
                    "rca2-flag-read-failure-v1", now, now, now.plusSeconds(1), false));
        }
    }

    private boolean strictBoolean(String key) {
        String value = environment.getProperty(key);
        if (value == null || (!value.equals("true") && !value.equals("false"))) {
            throw new IllegalArgumentException("missing or malformed boolean");
        }
        return Boolean.parseBoolean(value);
    }

    private int strictInteger(String key) {
        String value = environment.getProperty(key);
        if (value == null || !value.matches("-?[0-9]+")) throw new IllegalArgumentException("missing or malformed integer");
        return Integer.parseInt(value);
    }

    @Override public void close() { scheduler.shutdownNow(); }

    private static ThreadFactory daemonFactory() {
        return task -> {
            Thread thread = new Thread(task, "jc-rca2-flag-refresh");
            thread.setDaemon(true);
            return thread;
        };
    }
}
