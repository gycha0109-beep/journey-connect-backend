package com.jc.backend.search.shadow.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProductionSearchShadowOperationalLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionSearchShadowOperationalLogger.class);

    public void startup(ProductionSearchShadowRuntimeConfig config) {
        safeInfo("search_shadow_prod_startup enabled={} killSwitchActive={} configuredSamplingBps={} "
                        + "effectiveSamplingBps={} allowlistCount={} dispatchEligible={}",
                config.enabled(), config.killSwitchActive(), config.configuredSamplingBps(),
                config.effectiveSamplingBps(), config.allowlistHashes().size(), config.dispatchConfigured());
    }

    public void decision(ProductionSearchShadowActivationReason reason) {
        safeInfo("search_shadow_prod_decision reason={}", reason.safeCode());
    }

    public void completion(String outcome) {
        String safe = outcome != null && outcome.matches("[a-z][a-z0-9_]{0,63}") ? outcome : "unknown";
        safeInfo("search_shadow_prod_completion outcome={}", safe);
    }

    public void drill(String phase, boolean success) {
        String safe = phase != null && phase.matches("[a-z][a-z0-9_]{0,63}") ? phase : "unknown";
        safeInfo("search_shadow_prod_disable_drill phase={} success={}", safe, success);
    }

    private static void safeInfo(String format, Object... arguments) {
        try {
            LOGGER.info(format, arguments);
        } catch (RuntimeException ignored) {
            // Logging is observational and cannot fail the legacy endpoint.
        }
    }
}
