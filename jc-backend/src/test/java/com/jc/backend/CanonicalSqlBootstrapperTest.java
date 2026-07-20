package com.jc.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class CanonicalSqlBootstrapperTest {
    private static final List<String> SCRIPTS = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql",
            "07_recommendation_storage.sql",
            "08_recommendation_security_roles.sql",
            "09_recommendation_smoke_test.sql",
            "10_backend_runtime.sql",
            "11_backend_runtime_security_roles.sql",
            "12_backend_runtime_smoke_test.sql",
            "13_backend_role_routing.sql",
            "14_backend_role_routing_smoke_test.sql",
            "15_backend_role_runtime_fix.sql",
            "16_backend_role_runtime_fix_smoke_test.sql",
            "17_recommendation_run_exploration_partition_fix.sql",
            "18_recommendation_run_exploration_partition_fix_smoke_test.sql",
            "19_recommendation_replay_audit.sql",
            "20_recommendation_replay_audit_smoke_test.sql",
            "21_recommendation_behavior_runtime.sql",
            "22_recommendation_behavior_runtime_smoke_test.sql",
            "23_recommendation_p1_profile_policy.sql",
            "24_recommendation_p1_profile_policy_smoke_test.sql",
            "25_recommendation_p2_evaluation_release.sql",
            "26_recommendation_p2_evaluation_release_smoke_test.sql");

    @Test
    void splitsPostgresStatementsWithoutBreakingDollarQuotedBodies() {
        String sql = """
                BEGIN;
                CREATE FUNCTION public.example()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  NEW.value := 'a;''b';
                  RETURN NEW;
                END;
                $$;
                /* semicolon ; in comment */
                INSERT INTO public.sample(value) VALUES ('x;y');
                COMMIT;
                """;

        List<String> statements = CanonicalSqlBootstrapper.splitStatements(sql);

        assertThat(statements).hasSize(4);
        assertThat(statements.get(1)).contains("NEW.value := 'a;''b';").contains("$$");
        assertThat(statements.get(2)).contains("'x;y'");
    }

    @Test
    void splitsEveryCanonicalPostgresScript() throws Exception {
        for (String script : SCRIPTS) {
            String resource = "db/canonical/" + script;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(CanonicalSqlBootstrapper.splitStatements(sql))
                        .as(script)
                        .isNotEmpty()
                        .noneMatch(String::isBlank);
            }
        }
    }
}
