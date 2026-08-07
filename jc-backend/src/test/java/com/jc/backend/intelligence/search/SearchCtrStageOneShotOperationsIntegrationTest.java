package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchCtrStageOneShotOperationsIntegrationTest {

    private static final String BUILD_ID = "sr6fg-stage-" + "a".repeat(40);
    private static final Instant WINDOW_START = Instant.parse("2026-08-06T08:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-06T09:00:00Z");
    private static final String APPROVAL_REF = "approval:sr6fg-stage-20260806t0800z";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SearchCtrManualActivationPort activationPort;

    @Test
    void disposablePostgresValidatesPreflightGrantExecutionEvidenceAndRevoke() throws IOException {
        boolean createdBackendRole = !roleExists("jc_backend");
        if (createdBackendRole) {
            jdbcTemplate.execute("""
                    CREATE ROLE jc_backend
                      LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
                      NOREPLICATION NOBYPASSRLS
                    """);
        }

        try {
            assertThat(roleInherits("jc_reliability")).isFalse();
            executePsqlScript(
                    "operations/search-ctr/sr6fh/01_preflight_stage.sql",
                    hVariables());
            executePsqlScript(
                    "operations/search-ctr/sr6fg/01_grant_stage_reliability.sql",
                    gVariables());
            assertThat(hasReliabilityMembership()).isTrue();

            executePsqlScript(
                    "operations/search-ctr/sr6fg/02_verify_stage_reliability.sql",
                    gVariables());

            SearchCtrManualActivationPort.Result result = activationPort.execute(
                    new SearchCtrManualActivationPort.Command(
                            "search-ctr-manual-run:55555555555555555555555555555555",
                            WINDOW_START,
                            WINDOW_END,
                            "stage",
                            SearchCtrActivationPolicy.POLICY_VERSION,
                            Instant.parse("2026-08-07T00:00:00Z"),
                            "search-ctr:sr6fh-disposable-postgres-v1",
                            BUILD_ID));
            assertThat(result.writeStatus()).isEqualTo(SearchCtrProjectionPort.WriteStatus.STORED);
            assertThat(result.projectionStatus()).isEqualTo(SearchCtrContract.PROVISIONAL_STATUS);

            executePsqlScript(
                    "operations/search-ctr/sr6fh/04_collect_stage_evidence.sql",
                    hVariables());

            executePsqlScript(
                    "operations/search-ctr/sr6fg/03_revoke_stage_reliability.sql",
                    gVariables());
            assertThat(hasReliabilityMembership()).isFalse();
            executePsqlScript(
                    "operations/search-ctr/sr6fh/05_verify_stage_revoked.sql",
                    Map.of());
        } finally {
            rollbackQuietly();
            jdbcTemplate.execute("REVOKE jc_reliability FROM jc_backend");
            if (createdBackendRole) {
                jdbcTemplate.execute("DROP ROLE jc_backend");
            }
        }
    }

    private Map<String, String> hVariables() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sr6fh_environment", "stage");
        values.put("sr6fh_approval_ref", APPROVAL_REF);
        values.put("sr6fh_window_start", WINDOW_START.toString());
        values.put("sr6fh_producer_build_id", BUILD_ID);
        return values;
    }

    private Map<String, String> gVariables() {
        return Map.of(
                "sr6fg_environment", "stage",
                "sr6fg_approval_ref", APPROVAL_REF);
    }

    private void executePsqlScript(String relativePath, Map<String, String> variables)
            throws IOException {
        String sql = Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            sql = sql.replace(
                    ":'" + variable.getKey() + "'",
                    "'" + variable.getValue().replace("'", "''") + "'");
        }
        sql = sql.lines()
                .filter(line -> !line.stripLeading().startsWith("\\"))
                .reduce("", (left, right) -> left + right + "\n");
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException failure) {
            rollbackQuietly();
            throw failure;
        }
    }

    private void rollbackQuietly() {
        try {
            jdbcTemplate.execute("ROLLBACK");
        } catch (RuntimeException ignored) {
            // Best-effort recovery for a psql-style script that failed inside BEGIN.
        }
    }

    private boolean roleExists(String role) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_catalog.pg_roles where rolname = ?)",
                Boolean.class,
                role);
        return Boolean.TRUE.equals(exists);
    }

    private boolean roleInherits(String role) {
        Boolean inherits = jdbcTemplate.queryForObject(
                "select rolinherit from pg_catalog.pg_roles where rolname = ?",
                Boolean.class,
                role);
        return Boolean.TRUE.equals(inherits);
    }

    private boolean hasReliabilityMembership() {
        Boolean member = jdbcTemplate.queryForObject(
                "select pg_catalog.pg_has_role('jc_backend', 'jc_reliability', 'MEMBER')",
                Boolean.class);
        return Boolean.TRUE.equals(member);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("operations/search-ctr"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
