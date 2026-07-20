package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.assertExactCopy;
import static com.jc.backend.verification.StaticContractSupport.failContract;
import static com.jc.backend.verification.StaticContractSupport.requireContains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0BackendConvergenceStaticTest {
    private static final Path V19 = RepositoryLayout.resolve("database/journey-connect-db-v1.9");
    private static final Path V20 = RepositoryLayout.resolve("database/journey-connect-db-v2.0");
    private static final Path CANONICAL = RepositoryLayout.resolve(
            "jc-backend/src/test/resources/db/canonical");

    private static final List<String> BASELINE = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql",
            "07_recommendation_storage.sql",
            "08_recommendation_security_roles.sql",
            "09_recommendation_smoke_test.sql");
    private static final List<String> RUNTIME = List.of(
            "10_backend_runtime.sql",
            "11_backend_runtime_security_roles.sql",
            "12_backend_runtime_smoke_test.sql");

    @Test
    void v20ConvergesWithoutBaselineDriftOrFlywayAutodiscovery() throws IOException {
        for (String name : BASELINE) {
            assertExactCopy(V19.resolve(name), V20.resolve(name), "v1.9 baseline drift in DB v2.0");
        }
        for (String name : concat(BASELINE, RUNTIME)) {
            assertExactCopy(V20.resolve(name), CANONICAL.resolve(name), "canonical Testcontainers SQL drift");
        }

        String runtime = Files.readString(V20.resolve(RUNTIME.get(0)));
        String security = Files.readString(V20.resolve(RUNTIME.get(1)));
        String smoke = Files.readString(V20.resolve(RUNTIME.get(2)));
        String sample = RepositoryLayout.read("jc-backend/src/main/resources/application.yml.sample");
        String readme = RepositoryLayout.read("jc-backend/src/main/resources/db/README.md");

        requireContains(runtime, "CREATE TABLE public.refresh_tokens", "refresh-token table");
        requireContains(runtime, "CREATE TABLE public.crews", "crew table");
        requireContains(runtime, "CREATE TABLE public.crew_members", "crew-member table");
        requireContains(runtime, "UPDATE OF crew_id, user_id, status, reviewed_by", "reviewer trigger coverage");
        requireContains(runtime, "DEFERRABLE INITIALLY DEFERRED", "crew aggregate deferred integrity");
        requireContains(security, "TO jc_app, jc_recommendation;", "visibility function grant");
        requireContains(security, "public.can_user_view_post(bigint, bigint)", "visibility function signature");
        requireContains(security, "REVOKE DELETE, TRUNCATE ON public.crews FROM jc_app", "crew destructive privilege denial");
        requireContains(smoke, "Changing an approved membership reviewer", "reviewer bypass smoke");
        requireContains(smoke, "has_function_privilege", "function privilege smoke");
        requireContains(smoke, "ROLLBACK;", "runtime smoke rollback");
        requireContains(sample, "ddl-auto: validate", "Hibernate schema validation");
        requireContains(sample, "enabled: ${FLYWAY_ENABLED:false}", "Flyway fail-closed default");
        requireContains(readme, "migration-legacy/", "legacy migration isolation");
        requireContains(readme, "must not be enabled", "legacy migration warning");

        Path defaultFlyway = RepositoryLayout.resolve("jc-backend/src/main/resources/db/migration");
        if (Files.isDirectory(defaultFlyway)) {
            try (var files = Files.walk(defaultFlyway)) {
                if (files.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".sql"))) {
                    failContract("legacy or canonical SQL must not be auto-discovered under db/migration");
                }
            }
        }
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
