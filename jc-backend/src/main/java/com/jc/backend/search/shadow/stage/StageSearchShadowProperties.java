package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringMode;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.core.env.Environment;

/** Validated test/stage-only settings. No production resource supplies these properties. */
public record StageSearchShadowProperties(
        SearchShadowWiringMode mode,
        boolean explicitAllow,
        int sampleBasisPoints,
        Duration timeout,
        int queueCapacity,
        int maxConcurrency,
        int topK,
        String activeProfile) {
    public static final String PREFIX = "search.shadow.stage.";
    public static final int DEFAULT_SAMPLE_BASIS_POINTS = 0;
    public static final int DEFAULT_TIMEOUT_MILLIS = 200;
    public static final int DEFAULT_QUEUE_CAPACITY = 8;
    public static final int DEFAULT_MAX_CONCURRENCY = 2;
    public static final int DEFAULT_TOP_K = 10;

    public StageSearchShadowProperties {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (sampleBasisPoints < 0 || sampleBasisPoints > 10_000) {
            throw new IllegalArgumentException("sampleBasisPoints must be 0..10000");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalArgumentException("test/stage timeout must be greater than zero and at most five seconds");
        }
        if (queueCapacity < 1 || queueCapacity > 128) {
            throw new IllegalArgumentException("test/stage queueCapacity must be 1..128");
        }
        if (maxConcurrency < 1 || maxConcurrency > 16) {
            throw new IllegalArgumentException("test/stage maxConcurrency must be 1..16");
        }
        if (topK < 1 || topK > 100) throw new IllegalArgumentException("topK must be 1..100");
        if (!SearchShadowWiringConfigV1.TEST_PROFILE.equals(activeProfile)
                && !SearchShadowWiringConfigV1.STAGE_PROFILE.equals(activeProfile)) {
            throw new IllegalArgumentException("activeProfile must be an approved test/stage profile");
        }
        if (mode != SearchShadowWiringMode.TEST_ONLY) {
            throw new IllegalArgumentException("test/stage activation requires test_only mode");
        }
        if (!explicitAllow) throw new IllegalArgumentException("explicitAllow must be true for active wiring");
    }

    public static boolean activationAllowed(Environment environment) {
        try {
            return from(environment) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static StageSearchShadowProperties from(Environment environment) {
        if (environment == null) throw new IllegalArgumentException("environment is required");
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod") || profiles.contains("production")) return null;
        String activeProfile = profiles.contains(SearchShadowWiringConfigV1.TEST_PROFILE)
                ? SearchShadowWiringConfigV1.TEST_PROFILE
                : profiles.contains(SearchShadowWiringConfigV1.STAGE_PROFILE)
                        ? SearchShadowWiringConfigV1.STAGE_PROFILE : null;
        if (activeProfile == null) return null;
        SearchShadowWiringMode mode = SearchShadowWiringMode.parseOrDisabled(
                environment.getProperty(PREFIX + "mode", "disabled"));
        boolean allow = Boolean.TRUE.equals(environment.getProperty(PREFIX + "explicit-allow", Boolean.class, false));
        if (mode != SearchShadowWiringMode.TEST_ONLY || !allow) return null;
        int sample = integer(environment, "sample-basis-points", DEFAULT_SAMPLE_BASIS_POINTS);
        int timeoutMillis = integer(environment, "timeout-millis", DEFAULT_TIMEOUT_MILLIS);
        int queue = integer(environment, "queue-capacity", DEFAULT_QUEUE_CAPACITY);
        int concurrency = integer(environment, "max-concurrency", DEFAULT_MAX_CONCURRENCY);
        int topK = integer(environment, "top-k", DEFAULT_TOP_K);
        return new StageSearchShadowProperties(mode, true, sample, Duration.ofMillis(timeoutMillis),
                queue, concurrency, topK, activeProfile);
    }

    private static int integer(Environment environment, String name, int defaultValue) {
        String raw = environment.getProperty(PREFIX + name);
        if (raw == null || raw.isBlank()) return defaultValue;
        if (!raw.equals(raw.trim())) throw new IllegalArgumentException(name + " must be trimmed");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    public SearchShadowWiringConfigV1 wiringConfig() {
        return new SearchShadowWiringConfigV1(mode, activeProfile, explicitAllow, sampleBasisPoints, timeout,
                queueCapacity, maxConcurrency, new PolicyVersion("search-shadow-sampling-policy-v1"),
                new ProducerBuildId("ip10-test-stage-shadow"));
    }
}
