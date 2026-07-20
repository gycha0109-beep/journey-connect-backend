package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.assertEqualsWithLabel;
import static com.jc.backend.verification.StaticContractSupport.assertExactCopy;
import static com.jc.backend.verification.StaticContractSupport.failContract;
import static com.jc.backend.verification.StaticContractSupport.requireContains;
import static com.jc.backend.verification.StaticContractSupport.requireNoRegex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0RecommendationStorageStaticTest {
    private static final Path DB = RepositoryLayout.resolve("database/journey-connect-db-v1.9");
    private static final Path FLYWAY = RepositoryLayout.resolve(
            "jc-backend/src/main/resources/db/migration-v1_8");
    private static final Path TEST_SQL = RepositoryLayout.resolve(
            "jc-backend/src/test/resources/db/recommendation/09_recommendation_smoke_test.sql");

    private static final List<String> BASELINE = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql");

    private static final List<String> TABLES = List.of(
            "recommendation_snapshot",
            "recommendation_run",
            "recommendation_run_candidate",
            "recommendation_run_terminal_candidate",
            "recommendation_exposure_event",
            "recommendation_exposure_candidate",
            "recommendation_behavior_event");

    @Test
    void immutableBaselineAndStorageContractsRemainValid() throws IOException {
        verifyManifest();
        assertExactCopy(DB.resolve("07_recommendation_storage.sql"),
                FLYWAY.resolve("V7__recommendation_storage.sql"), "storage Flyway copy drift");
        assertExactCopy(DB.resolve("08_recommendation_security_roles.sql"),
                FLYWAY.resolve("V8__recommendation_security_roles.sql"), "security Flyway copy drift");
        assertExactCopy(DB.resolve("09_recommendation_smoke_test.sql"), TEST_SQL,
                "recommendation smoke-test copy drift");

        for (Path path : List.of(
                DB.resolve("07_recommendation_storage.sql"),
                DB.resolve("08_recommendation_security_roles.sql"),
                DB.resolve("09_recommendation_smoke_test.sql"),
                FLYWAY.resolve("V7__recommendation_storage.sql"),
                FLYWAY.resolve("V8__recommendation_security_roles.sql"),
                TEST_SQL)) {
            SqlLexicalVerifier.verify(path);
        }
        verifyStorageContracts();
    }

    private static void verifyManifest() throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(DB.resolve("V1_8_BASELINE_SHA256.txt"))) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) {
                failContract("invalid v1.8 checksum manifest line: " + rawLine);
            }
            expected.put(parts[1].trim(), parts[0]);
        }
        assertEqualsWithLabel(BASELINE, expected.keySet().stream().sorted().toList(),
                "v1.8 baseline manifest must cover exactly 01~06");
        for (String name : BASELINE) {
            assertEqualsWithLabel(expected.get(name), StaticContractSupport.sha256(DB.resolve(name)),
                    "v1.8 baseline drift: " + name);
        }
    }

    private static void verifyStorageContracts() throws IOException {
        String storage = Files.readString(DB.resolve("07_recommendation_storage.sql"));
        String security = Files.readString(DB.resolve("08_recommendation_security_roles.sql"));
        String smoke = Files.readString(DB.resolve("09_recommendation_smoke_test.sql"));

        requireContains(storage, "CREATE EXTENSION IF NOT EXISTS pgcrypto;", "pgcrypto dependency");
        requireContains(storage, "CREATE OR REPLACE FUNCTION public.recommendation_snapshot_sha256_hex", "snapshot hash function");
        requireContains(storage, "content_hash = public.recommendation_snapshot_sha256_hex(", "snapshot hash check");
        requireContains(storage, "journey-connect:snapshot:v1", "snapshot hash domain");
        requireContains(storage, "payload_fingerprint = public.recommendation_sha256_hex(canonical_payload)", "event hash check");
        requireContains(storage, "recommendation_run_request_uq UNIQUE (request_id)", "request idempotency");
        requireContains(storage, "result_snapshot_id varchar(128) NOT NULL", "result snapshot binding");
        requireContains(storage, "surface varchar(20) NOT NULL", "surface binding");
        requireContains(storage, "'ranking_result_v1'", "ranking-result kind");
        requireContains(storage, "payload_size_bytes BETWEEN 0 AND 16777216", "snapshot payload bound");
        requireContains(storage, "payload_size_bytes BETWEEN 0 AND 2097152", "exposure payload bound");
        requireContains(storage, "payload_size_bytes BETWEEN 0 AND 262144", "behavior payload bound");
        requireContains(storage, "UNIQUE (run_id, entity_key)", "candidate identity uniqueness");
        requireContains(storage, "recommendation_run_terminal_candidate_validate_source", "terminal source validation");
        requireContains(storage, "author_user.account_status = 'active'", "active author guard");
        requireContains(storage, "score BETWEEN 0.0 AND 1.0", "score domain");
        requireContains(storage, "seeded_tie_break_key BETWEEN 0 AND 4294967295", "unsigned tie-break range");
        requireContains(storage, "validate_recommendation_exposure_candidate_binding", "exposure binding");
        requireContains(storage, "validate_recommendation_behavior_event_binding", "behavior binding");
        requireContains(storage, "DEFERRABLE INITIALLY DEFERRED", "deferred integrity");

        for (String table : TABLES) {
            requireContains(storage, "CREATE TABLE public." + table, "table " + table);
            requireContains(storage, "CREATE TRIGGER " + table + "_append_only", "append-only trigger " + table);
        }

        requireContains(security, "CREATE ROLE jc_recommendation", "recommendation role");
        requireContains(security, "GRANT SELECT, INSERT ON public.recommendation_snapshot", "runtime insert grant");
        requireContains(security, "REVOKE UPDATE, DELETE, TRUNCATE ON public.recommendation_snapshot", "mutation revoke");
        requireContains(security, "GRANT SELECT ON public.recommendation_snapshot", "admin read grant");
        requireContains(security, "FROM jc_app, jc_auth, jc_admin;", "shared-role isolation");
        requireContains(security, "GRANT EXECUTE ON FUNCTION public.recommendation_sha256_hex(bytea) TO jc_recommendation;", "event hash execute grant");
        requireContains(security, "GRANT EXECUTE ON FUNCTION public.recommendation_snapshot_sha256_hex(varchar, varchar, bytea)", "snapshot hash execute grant");

        requireNoRegex(security, "GRANT\\s+UPDATE[^;]*TO\\s+jc_recommendation", "recommendation UPDATE grant");
        requireNoRegex(security, "GRANT\\s+DELETE[^;]*TO\\s+jc_recommendation", "recommendation DELETE grant");
        requireNoRegex(security, "GRANT\\s+TRUNCATE[^;]*TO\\s+jc_recommendation", "recommendation TRUNCATE grant");
        requireNoRegex(security, "GRANT\\s+INSERT[^;]*TO\\s+jc_app", "application INSERT grant");

        requireContains(smoke, "SET ROLE jc_recommendation;", "recommendation smoke role");
        requireContains(smoke, "SET ROLE jc_app;", "application smoke role");
        requireContains(smoke, "SET ROLE jc_auth;", "authentication smoke role");
        requireContains(smoke, "SET ROLE jc_admin;", "admin smoke role");
        requireContains(smoke, "'smoke-result-snapshot'", "ranking result snapshot smoke");
        requireContains(smoke, "expected 4 snapshots", "snapshot count smoke");
        requireContains(smoke, "WHEN SQLSTATE '55000'", "append-only owner-path smoke");
        requireContains(smoke, "ROLLBACK;", "smoke rollback");
    }
}
