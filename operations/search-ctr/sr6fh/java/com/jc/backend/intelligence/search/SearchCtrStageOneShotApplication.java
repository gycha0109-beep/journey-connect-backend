package com.jc.backend.intelligence.search;

import com.jc.backend.JcBackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Dedicated non-web process for the bounded SR-6F-H stage execution.
 *
 * <p>This operations-only class is compiled by the reviewed Gradle init script and is not part of
 * the normal backend artifact. It starts the authoritative Spring context, permits the default-off
 * manual ApplicationRunner to execute exactly once when all gates pass, and closes the context
 * immediately after startup completes.
 */
public final class SearchCtrStageOneShotApplication {

    private SearchCtrStageOneShotApplication() {}

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JcBackendApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ignored = application.run(args)) {
            // ApplicationRunner execution completes before SpringApplication.run(...) returns.
        }
    }
}
