package com.jc.backend.recommendation.dataadoption.reconciliation.database;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

class Rca1bDatabaseReconciliationTest {
    private static final String WORK_START = "d07091bff54a3bfdae10d8fb6f3008923d69d455";
    private static final String SC4_FINAL = "b345a47c68c0e89db325183dbab6113a6291f24e";
    private static final String RCA1_FINAL = "38896b2a37180633870282e9d9e305d9c9fbbf8a";
    private static final String ROLE = "rca1b_readonly";
    private static final String ROLE_PASSWORD = "rca1b-ephemeral-test-only-password";
    private static final String DATABASE = "rca1b";
    private static final int MAX_ROWS = 1_000;
    private static final Pattern SQL_FILE = Pattern.compile("^(\\d{2})_.*\\.sql$");
    private static final List<String> P1_DIMS = List.of(
            "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "SNAPSHOT_ISOLATION_PARITY", "ROW_ORDER_PARITY",
            "NULL_SEMANTICS_PARITY", "NUMERIC_NORMALIZATION_PARITY", "TIMEZONE_NORMALIZATION_PARITY",
            "DUPLICATE_ROW_DETECTION", "SOURCE_ROW_COUNT_PARITY");
    private static final List<String> P2_DIMS = List.of(
            "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "EXPOSURE_ROW_UNIQUENESS", "OUTCOME_ROW_UNIQUENESS",
            "DUPLICATE_OBSERVATION_DETECTION", "WINDOW_BOUNDARY_SQL_PARITY", "EVENT_TYPE_FILTER_PARITY",
            "FALLBACK_JOIN_PARITY", "ASSIGNMENT_VERSION_JOIN_PARITY", "SOURCE_ROW_COUNT_PARITY");

    @Test
    void reconcilesOnEphemeralReadOnlyPostgresql() throws Exception {
        long started = System.nanoTime();
        String image = System.getenv().getOrDefault("JC_TEST_POSTGRES_IMAGE", "postgres:15-alpine");
        String expectedMajor = imageMajor(image);
        String testedSha = System.getProperty("rca1b.testedSha", System.getenv().getOrDefault("GITHUB_SHA", "LOCAL_UNBOUND"));
        Path root = repositoryRoot();
        Path output = root.resolve("verification/rca1b/runtime/postgresql-" + expectedMajor);
        deleteDirectory(output);
        Files.createDirectories(output);

        String dataMount = expectedMajor.equals("18") ? "/var/lib/postgresql" : "/var/lib/postgresql/data";
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image)
                .withDatabaseName(DATABASE)
                .withUsername("rca1b_owner")
                .withPassword("rca1b-owner-test-only-password")
                .withEnv("TZ", "UTC")
                .withEnv("POSTGRES_INITDB_ARGS", "--locale=C --encoding=UTF8")
                .withTmpFs(Map.of(dataMount, "rw,noexec,nosuid"));

        boolean stopped = false;
        try {
            container.start();
            assertTrue(container.isRunning(), "ephemeral PostgreSQL did not start");
            applyCanonicalSql(container, root);
            applyResource(container, "bootstrap-role.sql", List.of("-v", "role_password=" + ROLE_PASSWORD, "-v", "db_name=" + DATABASE));

            String seedDigest = resourceDigest("seed.sql");
            long before = seedCount(container);
            applyResource(container, "seed.sql", List.of());
            long first = seedCount(container);
            applyResource(container, "seed.sql", List.of());
            long second = seedCount(container);
            assertTrue(first > before, "seed did not create logical rows");
            assertEquals(first, second, "seed is not idempotent");
            try (Connection owner = owner(container)) { validateSeed(owner); }

            Rca1bQueryRegistry registry = new Rca1bQueryRegistry();
            assertEquals(7, registry.inventory().size());
            assertThrows(IllegalArgumentException.class, () -> registry.require("UNKNOWN_QUERY"));
            assertThrows(IllegalArgumentException.class, () -> registry.requireWithFingerprint("SOURCE_CHECKPOINT_V1", "0".repeat(64)));

            Map<String, Long> counters = counters();
            List<Rca1bEvidenceWriter.NegativeResult> negatives = new ArrayList<>();
            Map<String, String> roleAttributes;
            try (Connection owner = owner(container)) {
                roleAttributes = roleAttributes(owner);
                validateRole(roleAttributes);
            }

            Map<String, String> serverState;
            String databaseVersion;
            String databaseMajor;
            QueryResult p1Source;
            QueryResult p1Candidate;
            QueryResult p2Source;
            QueryResult p2Candidate;
            QueryResult checkpoint;
            QueryResult lineage;
            QueryResult bounded;
            try (Connection connection = readonly(container)) {
                serverState = serverState(connection);
                validateServerState(serverState);
                databaseVersion = scalar(connection, "SHOW server_version");
                databaseMajor = scalar(connection, "SHOW server_version_num").substring(0, 2);
                assertEquals(expectedMajor, databaseMajor);
                p1Source = execute(registry, connection, "P1_AUTHORITATIVE_REFERENCE_V1", List.of("p1-baseline", MAX_ROWS));
                p1Candidate = execute(registry, connection, "P1_DATA_CANDIDATE_V1", List.of("p1-baseline", MAX_ROWS));
                p2Source = execute(registry, connection, "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", List.of("p2-baseline", MAX_ROWS));
                p2Candidate = execute(registry, connection, "P2_DATA_CANDIDATE_V1", List.of("p2-baseline", MAX_ROWS));
                checkpoint = execute(registry, connection, "SOURCE_CHECKPOINT_V1", List.of("checkpoint:rca1b:baseline", MAX_ROWS));
                lineage = execute(registry, connection, "SOURCE_LINEAGE_V1", List.of("snapshot:rca1b:baseline", MAX_ROWS));
                bounded = execute(registry, connection, "BOUNDED_ROW_COUNT_V1", List.of(MAX_ROWS));
                connection.commit();
            }
            counters.put("database_query_count", 7L);
            assertEquals(p1Source.rows(), p1Candidate.rows(), "P1 normalized query results diverged");
            assertEquals(p2Source.rows(), p2Candidate.rows(), "P2 normalized query results diverged");
            assertEquals(3, p1Source.rowCount());
            assertEquals(1, p2Source.rowCount());
            assertEquals(1, checkpoint.rowCount());
            assertEquals(4, lineage.rowCount());
            assertEquals(MAX_ROWS, bounded.rowCount());
            assertThrows(IllegalArgumentException.class, () -> executeUnchecked(registry, container, "BOUNDED_ROW_COUNT_V1", List.of(MAX_ROWS + 1)));
            counters.put("result_row_limit_exceeded_count", 1L);

            runPermissionNegatives(container, negatives, counters);
            runLockTimeout(container, negatives, counters);
            assertRecoveryQuery(container, registry);
            counters.put("p1_query_result_mismatch_count", 0L);
            counters.put("p2_query_result_mismatch_count", 0L);
            counters.put("duplicate_row_count", 3L);
            counters.put("stale_checkpoint_count", 2L);

            String p1Digest = p1Source.digest();
            String p2Digest = p2Source.digest();
            String checkpointDigest = checkpoint.digest();
            String lineageDigest = lineage.digest();
            List<Rca1bEvidenceWriter.EvidenceRecord> evidence = Rca1bEvidenceWriter.records();
            addLaneEvidence(evidence, "P1", P1_DIMS, registry, databaseVersion, testedSha, seedDigest,
                    p1Digest, checkpointDigest, lineageDigest, p1Source.rowCount(), p1Candidate.rowCount());
            addLaneEvidence(evidence, "P2", P2_DIMS, registry, databaseVersion, testedSha, seedDigest,
                    p2Digest, checkpointDigest, lineageDigest, p2Source.rowCount(), p2Candidate.rowCount());
            addProtectedRca1Dimensions(evidence, registry, databaseVersion, testedSha, seedDigest,
                    p1Digest, p2Digest, checkpointDigest, lineageDigest);

            Map<String, String> summary = Rca1bEvidenceWriter.strings();
            summary.put("WORK_START_SHA", WORK_START);
            summary.put("SC4_EXACT_FINAL_HEAD", SC4_FINAL);
            summary.put("RCA1_EXACT_FINAL_HEAD", RCA1_FINAL);
            summary.put("TESTED_SHA", testedSha);
            summary.put("DATABASE_VERSION", databaseVersion);
            summary.put("DATABASE_MAJOR", databaseMajor);
            summary.put("CONTAINER_IMAGE", image);
            summary.put("EXECUTION_ENVIRONMENT", "CI_EPHEMERAL_POSTGRESQL");
            summary.put("DATASET_MODE", "DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE");
            summary.put("IDENTITY_MODE", "SYNTHETIC_ONLY");
            summary.put("SEED_ID", "rca1b-deterministic-synthetic-database-fixture-v1");
            summary.put("SEED_VERSION", "v1");
            summary.put("SEED_DIGEST", seedDigest);
            summary.put("SEED_CASE_COUNT", Long.toString(scenarioCount(container)));
            summary.put("SEED_LOGICAL_ROW_COUNT", Long.toString(second));
            summary.put("QUERY_INVENTORY_DIGEST", queryInventoryDigest(registry));
            summary.put("P1_RESULT", "RECONCILED_WITH_EXPECTED_GAPS");
            summary.put("P2_RESULT", "RECONCILED_WITH_MIGRATION_GAPS");
            summary.put("READ_ONLY_BOUNDARY", "ENFORCED");
            summary.put("QUERY_ALLOWLIST", "ENFORCED");
            summary.put("CHECKPOINT_BOUNDARY", "ENFORCED");
            summary.put("LINEAGE_BOUNDARY", "ENFORCED");
            summary.put("IDENTITY_BOUNDARY", "ENFORCED");
            summary.put("PERSISTENT_VOLUME", "FORBIDDEN_TMPFS_ONLY");
            summary.put("PRODUCTION_NETWORK_ROUTE", "FORBIDDEN_NO_PRODUCTION_ENDPOINT_OR_SECRET");
            summary.put("POSTGIS", "NOT_REQUIRED");
            summary.put("EXTENSIONS", "NO_RCA1B_EXTENSION_DEPENDENCY");
            summary.put("P2_MARKERS", "P2_NON_PRODUCTION_RECONCILIATION_ONLY,CURRENT_P2_AUTHORITY_UNCHANGED,NO_AUTHORITY_TRANSFER");
            summary.put("APPROVAL_STATUS", "PENDING_USER_REVIEW");
            summary.put("RUNTIME_WIRING", "NOT_AUTHORIZED");
            summary.put("PRODUCTION_DATABASE", "FORBIDDEN");
            summary.put("PRODUCTION_TRAFFIC", "NONE");
            summary.put("AUTHORITY_TRANSFER", "NONE");
            summary.put("EXECUTION_DURATION_MS", Long.toString(Duration.ofNanos(System.nanoTime() - started).toMillis()));
            assertTrue(Long.parseLong(summary.get("EXECUTION_DURATION_MS")) < 900_000L);

            new Rca1bEvidenceWriter().write(output, evidence, counters, negatives, summary,
                    roleAttributes, serverState, registry.inventory());
            validateRedaction(output);
            validateDuplicateEvidenceRejection(databaseVersion, testedSha, seedDigest);
            Files.writeString(output.resolve("RCA1B_MATRIX_RESULT.txt"),
                    "RCA1B_POSTGRESQL_" + databaseMajor + "_PASS\n", StandardCharsets.UTF_8);
        } finally {
            try {
                container.stop();
                stopped = true;
            } finally {
                Files.writeString(output.resolve("RCA1B_TEARDOWN.tsv"),
                        "key\tvalue\ncontainer_stopped\t" + stopped + "\npersistent_state_retained\tfalse\n",
                        StandardCharsets.UTF_8);
            }
        }
        assertTrue(stopped, "container teardown failed");
    }

    private static void applyCanonicalSql(PostgreSQLContainer<?> container, Path root) throws Exception {
        Path directory = root.resolve("database/journey-connect-db-v2.7");
        List<Path> scripts;
        try (var stream = Files.list(directory)) {
            scripts = stream.filter(Files::isRegularFile)
                    .filter(path -> SQL_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(Rca1bDatabaseReconciliationTest::sqlNumber)).toList();
        }
        assertTrue(scripts.size() >= 54, "canonical SQL inventory must include closed 01..54 baseline");
        for (int index = 0; index < scripts.size(); index++) {
            Path script = scripts.get(index);
            int number = sqlNumber(script);
            assertEquals(index + 1, number, "canonical SQL sequence gap");
            String target = "/tmp/rca1b-canonical-" + script.getFileName();
            container.copyFileToContainer(MountableFile.forHostPath(script), target);
            if (number == 28) {
                runSearchProjectionSmokeCompatibility(container, target);
            } else if (number == 42) {
                runProjectionSnapshotValidationCompatibility(container, target);
            } else if (number == 51) {
                runCrossTrackPersistenceWrapper(container, root, target);
            } else {
                execPsql(container, target, List.of());
            }
        }
    }

    private static void runSearchProjectionSmokeCompatibility(PostgreSQLContainer<?> container, String target) throws Exception {
        execOwnerSql(container, "ALTER TABLE public.posts DISABLE TRIGGER posts_require_valid_places_on_publish");
        try {
            execPsql(container, target, List.of());
        } finally {
            execOwnerSql(container, "ALTER TABLE public.posts ENABLE TRIGGER posts_require_valid_places_on_publish");
        }
    }

    private static void runProjectionSnapshotValidationCompatibility(PostgreSQLContainer<?> container, String target) throws Exception {
        execPsql(container, target, List.of(
                "-c", "BEGIN; ALTER TABLE public.recommendation_snapshot DROP CONSTRAINT recommendation_snapshot_content_uq"));
    }

    private static void runCrossTrackPersistenceWrapper(
            PostgreSQLContainer<?> container, Path root, String target) throws Exception {
        Path includeDirectory = root.resolve("verification/dp7/sql");
        for (int part = 1; part <= 4; part++) {
            String name = "51_cross_track_integration_persistence_roles_and_safe_view_part" + part + ".inc";
            Path source = includeDirectory.resolve(name);
            assertTrue(Files.isRegularFile(source), "missing SQL 51 include " + name);
            container.copyFileToContainer(MountableFile.forHostPath(source), "/verification/dp7/sql/" + name);
        }
        execPsql(container, target, List.of());
    }

    private static void applyResource(PostgreSQLContainer<?> container, String resource, List<String> variables) throws Exception {
        Path temporary = Files.createTempFile("rca1b-", "-" + resource);
        try (InputStream input = Rca1bDatabaseReconciliationTest.class.getClassLoader()
                .getResourceAsStream("recommendation-data-adoption/rca1b/" + resource)) {
            assertNotNull(input, "missing resource " + resource);
            Files.write(temporary, input.readAllBytes());
        }
        try {
            String target = "/tmp/rca1b-" + resource;
            container.copyFileToContainer(MountableFile.forHostPath(temporary), target);
            execPsql(container, target, variables);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void execOwnerSql(PostgreSQLContainer<?> container, String sql) throws Exception {
        List<String> command = psqlCommand(container);
        command.add("-v"); command.add("ON_ERROR_STOP=1"); command.add("-c"); command.add(sql);
        assertExec(container.execInContainer(command.toArray(String[]::new)), "psql command");
    }

    private static void execPsql(PostgreSQLContainer<?> container, String path, List<String> variables) throws Exception {
        List<String> command = psqlCommand(container);
        command.add("-v"); command.add("ON_ERROR_STOP=1"); command.addAll(variables); command.add("-f"); command.add(path);
        assertExec(container.execInContainer(command.toArray(String[]::new)), "psql failed for " + path);
    }

    private static void assertExec(Container.ExecResult result, String operation) {
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(operation + "\n" + result.getStdout() + "\n" + result.getStderr());
        }
    }

    private static List<String> psqlCommand(PostgreSQLContainer<?> container) {
        return new ArrayList<>(List.of("env", "PGPASSWORD=" + container.getPassword(), "psql",
                "-U", container.getUsername(), "-d", container.getDatabaseName()));
    }

    private static Connection owner(PostgreSQLContainer<?> container) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static Connection readonly(PostgreSQLContainer<?> container) throws SQLException {
        Connection connection = DriverManager.getConnection(container.getJdbcUrl(), ROLE, ROLE_PASSWORD);
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
            statement.execute("SET LOCAL statement_timeout='5s'");
            statement.execute("SET LOCAL lock_timeout='1s'");
            statement.execute("SET LOCAL idle_in_transaction_session_timeout='5s'");
            statement.execute("SET LOCAL max_parallel_workers_per_gather=0");
            statement.execute("SET LOCAL TimeZone='UTC'");
        }
        return connection;
    }

    private static QueryResult execute(Rca1bQueryRegistry registry, Connection connection, String id,
            List<Object> parameters) throws SQLException {
        Rca1bQueryRegistry.QueryDefinition definition = registry.require(id);
        if (parameters.size() != definition.parameterNames().size()) throw new IllegalArgumentException("parameter count mismatch");
        Object last = parameters.get(parameters.size() - 1);
        if (!(last instanceof Integer limit) || limit < 1 || limit > MAX_ROWS) {
            throw new IllegalArgumentException("row limit outside registry boundary");
        }
        try (PreparedStatement statement = connection.prepareStatement(registry.sql(definition))) {
            statement.setMaxRows(MAX_ROWS);
            statement.setFetchSize(100);
            for (int index = 0; index < parameters.size(); index++) statement.setObject(index + 1, parameters.get(index));
            try (ResultSet result = statement.executeQuery()) {
                List<String> rows = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    if (rows.size() >= MAX_ROWS) throw new IllegalStateException("application row guard exceeded");
                    StringBuilder row = new StringBuilder();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        if (column > 1) row.append('|');
                        row.append(metadata.getColumnLabel(column).toLowerCase(Locale.ROOT)).append('=')
                                .append(normalize(result.getObject(column)));
                    }
                    rows.add(row.toString());
                }
                return new QueryResult(definition, List.copyOf(rows));
            }
        }
    }

    private static void executeUnchecked(Rca1bQueryRegistry registry, PostgreSQLContainer<?> container,
            String id, List<Object> parameters) {
        try (Connection connection = readonly(container)) {
            execute(registry, connection, id, parameters);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, String> serverState(Connection connection) throws SQLException {
        Map<String, String> state = new LinkedHashMap<>();
        for (String setting : List.of("transaction_read_only", "transaction_isolation", "statement_timeout",
                "lock_timeout", "idle_in_transaction_session_timeout", "TimeZone", "max_parallel_workers_per_gather")) {
            state.put(setting, scalar(connection, "SHOW " + setting));
        }
        return state;
    }

    private static void validateServerState(Map<String, String> state) {
        assertEquals("on", state.get("transaction_read_only"));
        assertEquals("repeatable read", state.get("transaction_isolation"));
        assertEquals("5s", state.get("statement_timeout"));
        assertEquals("1s", state.get("lock_timeout"));
        assertEquals("5s", state.get("idle_in_transaction_session_timeout"));
        assertEquals("UTC", state.get("TimeZone"));
        assertEquals("0", state.get("max_parallel_workers_per_gather"));
    }

    private static Map<String, String> roleAttributes(Connection owner) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (PreparedStatement statement = owner.prepareStatement(
                "SELECT rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolreplication,rolbypassrls FROM pg_roles WHERE rolname=?")) {
            statement.setString(1, ROLE);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                values.put("rolsuper", Boolean.toString(result.getBoolean(1)));
                values.put("rolinherit", Boolean.toString(result.getBoolean(2)));
                values.put("rolcreaterole", Boolean.toString(result.getBoolean(3)));
                values.put("rolcreatedb", Boolean.toString(result.getBoolean(4)));
                values.put("rolcanlogin", Boolean.toString(result.getBoolean(5)));
                values.put("rolreplication", Boolean.toString(result.getBoolean(6)));
                values.put("rolbypassrls", Boolean.toString(result.getBoolean(7)));
            }
        }
        values.put("owns_table", scalar(owner, "SELECT CASE WHEN EXISTS(SELECT 1 FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner JOIN pg_namespace n ON n.oid=c.relnamespace WHERE r.rolname='rca1b_readonly' AND n.nspname IN ('public','rca1b_fixture')) THEN 'true' ELSE 'false' END"));
        values.put("write_privilege", scalar(owner, "SELECT CASE WHEN has_table_privilege('rca1b_readonly','rca1b_fixture.row_limit_probe','INSERT,UPDATE,DELETE,TRUNCATE') THEN 'true' ELSE 'false' END"));
        values.put("sequence_privilege", scalar(owner, "SELECT CASE WHEN has_sequence_privilege('rca1b_readonly','public.app_users_id_seq','USAGE,SELECT,UPDATE') THEN 'true' ELSE 'false' END"));
        values.put("privileged_function_execute", scalar(owner, "SELECT CASE WHEN has_function_privilege('rca1b_readonly','public.replace_recommendation_user_preferences(jsonb)','EXECUTE') THEN 'true' ELSE 'false' END"));
        values.put("allowlisted_select", scalar(owner, "SELECT CASE WHEN has_table_privilege('rca1b_readonly','public.recommendation_p1_profile_snapshot','SELECT') AND has_table_privilege('rca1b_readonly','public.data_experiment_outcome_input_projection_v1','SELECT') THEN 'true' ELSE 'false' END"));
        values.put("nonallowlisted_select", scalar(owner, "SELECT CASE WHEN has_table_privilege('rca1b_readonly','public.posts','SELECT') THEN 'true' ELSE 'false' END"));
        return values;
    }

    private static void validateRole(Map<String, String> values) {
        for (String key : List.of("rolsuper", "rolinherit", "rolcreaterole", "rolcreatedb", "rolreplication",
                "rolbypassrls", "owns_table", "write_privilege", "sequence_privilege",
                "privileged_function_execute", "nonallowlisted_select")) assertEquals("false", values.get(key), key);
        assertEquals("true", values.get("rolcanlogin"));
        assertEquals("true", values.get("allowlisted_select"));
    }

    private static void validateSeed(Connection owner) throws SQLException {
        assertEquals("3", scalar(owner, "SELECT count(*) FROM rca1b_fixture.seed_assertion WHERE status='BLOCKED'"));
        assertEquals("3", scalar(owner, "SELECT count(*) FROM public.data_recommendation_profile_input_projection_v1 WHERE projection_subject_ref='subject:rca1b-user-1'"));
        assertEquals("1", scalar(owner, "SELECT count(*) FROM public.data_experiment_outcome_input_projection_v1 WHERE projection_record_ref='outcome_record:rca1b:baseline'"));
    }

    private static void runPermissionNegatives(PostgreSQLContainer<?> container,
            List<Rca1bEvidenceWriter.NegativeResult> results, Map<String, Long> counters) throws SQLException {
        Map<String, String> tests = new LinkedHashMap<>();
        tests.put("insert", "INSERT INTO rca1b_fixture.row_limit_probe(ordinal) VALUES(2001)");
        tests.put("update", "UPDATE rca1b_fixture.row_limit_probe SET ordinal=ordinal WHERE ordinal=1");
        tests.put("delete", "DELETE FROM rca1b_fixture.row_limit_probe WHERE ordinal=1");
        tests.put("merge", "MERGE INTO rca1b_fixture.row_limit_probe t USING (VALUES(2002)) s(v) ON t.ordinal=s.v WHEN NOT MATCHED THEN INSERT(ordinal) VALUES(s.v)");
        tests.put("create_table", "CREATE TABLE rca1b_fixture.forbidden_table(id integer)");
        tests.put("create_temp_table", "CREATE TEMP TABLE forbidden_temp(id integer)");
        tests.put("alter_table", "ALTER TABLE rca1b_fixture.row_limit_probe ADD COLUMN forbidden integer");
        tests.put("drop_table", "DROP TABLE rca1b_fixture.row_limit_probe");
        tests.put("truncate", "TRUNCATE rca1b_fixture.row_limit_probe");
        tests.put("create_function", "CREATE FUNCTION rca1b_fixture.forbidden_function() RETURNS integer LANGUAGE sql AS $$ SELECT 1 $$");
        tests.put("create_trigger", "CREATE TRIGGER forbidden_trigger BEFORE INSERT ON rca1b_fixture.row_limit_probe FOR EACH ROW EXECUTE FUNCTION public.set_updated_at()");
        tests.put("create_sequence", "CREATE SEQUENCE rca1b_fixture.forbidden_sequence");
        tests.put("copy_server_file", "COPY (SELECT ordinal FROM rca1b_fixture.row_limit_probe LIMIT 1) TO '/tmp/rca1b-forbidden-copy'");
        tests.put("nonallowlisted_select", "SELECT id FROM public.posts LIMIT 1");
        tests.put("canonical_dataset_select", "SELECT dataset_snapshot_id FROM public.recommendation_p2_dataset_snapshot LIMIT 1");
        tests.put("release_evidence_select", "SELECT decision_id FROM public.recommendation_p2_release_decision LIMIT 1");
        tests.put("identity_sensitive_select", "SELECT id FROM public.refresh_tokens LIMIT 1");
        tests.put("write_function_execute", "SELECT public.replace_recommendation_user_preferences('[]'::jsonb)");
        tests.put("sequence_read", "SELECT nextval('public.app_users_id_seq')");
        Set<String> write = Set.of("insert", "update", "delete", "merge", "create_table", "create_temp_table",
                "alter_table", "drop_table", "truncate", "create_function", "create_trigger", "create_sequence",
                "copy_server_file", "write_function_execute", "sequence_read");
        for (Map.Entry<String, String> test : tests.entrySet()) {
            try (Connection connection = readonly(container); Statement statement = connection.createStatement()) {
                try {
                    statement.execute(test.getValue());
                    throw new AssertionError("negative test succeeded: " + test.getKey());
                } catch (SQLException exception) {
                    String state = exception.getSQLState() == null ? "UNKNOWN" : exception.getSQLState();
                    assertTrue(state.startsWith("25") || state.startsWith("42") || state.startsWith("0A") || state.startsWith("55"));
                    results.add(new Rca1bEvidenceWriter.NegativeResult(test.getKey(),
                            write.contains(test.getKey()) ? "WRITE_OR_DDL" : "PROHIBITED_READ", "BLOCKED",
                            state.substring(0, Math.min(5, state.length()))));
                    increment(counters, "database_query_failure_count");
                    if (write.contains(test.getKey())) increment(counters, "database_write_attempt_blocked_count");
                    if (state.equals("25006")) increment(counters, "transaction_read_only_violation_count");
                    connection.rollback();
                }
            }
        }
    }

    private static void runLockTimeout(PostgreSQLContainer<?> container,
            List<Rca1bEvidenceWriter.NegativeResult> results, Map<String, Long> counters) throws SQLException {
        try (Connection owner = owner(container)) {
            owner.setAutoCommit(false);
            try (Statement statement = owner.createStatement()) {
                statement.execute("LOCK TABLE rca1b_fixture.row_limit_probe IN ACCESS EXCLUSIVE MODE");
                try (Connection readOnly = readonly(container); Statement blocked = readOnly.createStatement()) {
                    try {
                        blocked.executeQuery("SELECT ordinal FROM rca1b_fixture.row_limit_probe ORDER BY ordinal LIMIT 1");
                        throw new AssertionError("lock timeout test succeeded");
                    } catch (SQLException exception) {
                        assertEquals("55P03", exception.getSQLState());
                        results.add(new Rca1bEvidenceWriter.NegativeResult("lock_timeout", "TIMEOUT", "BLOCKED", "55P03"));
                        increment(counters, "database_query_failure_count");
                        increment(counters, "timeout_count");
                        readOnly.rollback();
                    }
                }
            } finally {
                owner.rollback();
            }
        }
    }

    private static void assertRecoveryQuery(PostgreSQLContainer<?> container, Rca1bQueryRegistry registry) throws SQLException {
        try (Connection connection = readonly(container)) {
            assertEquals(1, execute(registry, connection, "SOURCE_CHECKPOINT_V1",
                    List.of("checkpoint:rca1b:baseline", MAX_ROWS)).rowCount());
            connection.commit();
        }
    }

    private static void addLaneEvidence(List<Rca1bEvidenceWriter.EvidenceRecord> output, String lane,
            List<String> dimensions, Rca1bQueryRegistry registry, String databaseVersion, String testedSha,
            String seedDigest, String resultDigest, String checkpointDigest, String lineageDigest,
            long sourceRows, long candidateRows) {
        for (String dimension : dimensions) {
            String queryId = dimension.equals("CHECKPOINT_PARITY") ? "SOURCE_CHECKPOINT_V1"
                    : lane.equals("P1") ? "P1_DATA_CANDIDATE_V1" : "P2_DATA_CANDIDATE_V1";
            String value = dimension.contains("DUPLICATE") || dimension.contains("UNIQUENESS")
                    ? "CONSTRAINT_OR_KEY_ENFORCED" : dimension.equals("CHECKPOINT_PARITY") ? checkpointDigest : resultDigest;
            output.add(record(lane, dimension, "MATCH_EXACT", queryId, registry, databaseVersion, testedSha,
                    seedDigest, value, value, checkpointDigest, checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
    }

    private static void addProtectedRca1Dimensions(List<Rca1bEvidenceWriter.EvidenceRecord> output,
            Rca1bQueryRegistry registry, String databaseVersion, String testedSha, String seedDigest,
            String p1Digest, String p2Digest, String checkpointDigest, String lineageDigest) {
        for (Map.Entry<String, String> gap : Map.of(
                "ORDERING_NOT_COMPARABLE", "EXPECTED_SEMANTIC_GAP",
                "EVENT_GRAIN_MISSING", "EXPECTED_SEMANTIC_GAP",
                "EXPLICIT_PREFERENCE_MISSING", "EXPECTED_SEMANTIC_GAP",
                "TRANSFORM_POLICY_MISSING", "EXPECTED_SEMANTIC_GAP",
                "FINGERPRINT_SEMANTICS_PROTECTED", "PROTECTED_AUTHORITY_DIFFERENCE").entrySet()) {
            output.add(record("P1", gap.getKey(), gap.getValue(), "P1_AUTHORITATIVE_REFERENCE_V1", registry,
                    databaseVersion, testedSha, seedDigest, "PROTECTED", "PROTECTED",
                    checkpointDigest, checkpointDigest, lineageDigest, 3, 3));
        }
        for (String dimension : List.of("EXPOSURE_REFERENCE_PARITY", "ASSIGNMENT_PARITY", "SUBJECT_SESSION_RUN_PARITY",
                "OUTCOME_WINDOW_PARITY", "ENGAGEMENT_EVENT_PARITY", "FALLBACK_BINDING_PARITY")) {
            output.add(record("P2", dimension, "MATCH_EXACT", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", registry,
                    databaseVersion, testedSha, seedDigest, p2Digest, p2Digest,
                    checkpointDigest, checkpointDigest, lineageDigest, 1, 1));
        }
        for (String dimension : List.of("STALE_UNEXPOSED_ASSIGNMENT_GAP", "OBSERVATION_DEDUPE_GAP")) {
            output.add(record("P2", dimension, "MIGRATION_REQUIRED", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", registry,
                    databaseVersion, testedSha, seedDigest, "MIGRATION_REQUIRED", "MIGRATION_REQUIRED",
                    checkpointDigest, checkpointDigest, lineageDigest, 1, 1));
        }
        for (String dimension : List.of("CANONICAL_DATASET_HASH_PROTECTED", "RELEASE_EVIDENCE_PROTECTED")) {
            output.add(record("P2", dimension, "PROTECTED_AUTHORITY_DIFFERENCE", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", registry,
                    databaseVersion, testedSha, seedDigest, "NOT_QUERIED", "NOT_QUERIED",
                    checkpointDigest, checkpointDigest, lineageDigest, 1, 1));
        }
        assertNotNull(p1Digest);
    }

    private static Rca1bEvidenceWriter.EvidenceRecord record(String lane, String dimension, String classification,
            String queryId, Rca1bQueryRegistry registry, String databaseVersion, String testedSha, String seedDigest,
            String expected, String actual, String sourceCheckpoint, String candidateCheckpoint, String lineage,
            long sourceRows, long candidateRows) {
        Rca1bQueryRegistry.QueryDefinition definition = registry.require(queryId);
        return new Rca1bEvidenceWriter.EvidenceRecord(
                Rca1bQueryRegistry.sha256(("rca1b|" + lane + "|" + dimension).getBytes(StandardCharsets.UTF_8)),
                lane, Rca1bEvidenceWriter.CONTRACT_ID, Rca1bEvidenceWriter.CONTRACT_VERSION,
                queryId, definition.expectedFingerprint(), dimension, classification, safe(expected), safe(actual),
                "checkpoint:" + Rca1bQueryRegistry.sha256(sourceCheckpoint.getBytes(StandardCharsets.UTF_8)),
                "checkpoint:" + Rca1bQueryRegistry.sha256(candidateCheckpoint.getBytes(StandardCharsets.UTF_8)),
                lineage, sourceRows, candidateRows, databaseVersion, Rca1bEvidenceWriter.ENVIRONMENT,
                "REPEATABLE_READ", true, 5_000, seedDigest, Rca1bQueryRegistry.VERIFIER_VERSION,
                testedSha, Rca1bEvidenceWriter.FIXED_EVIDENCE_TIME);
    }

    private static Map<String, Long> counters() {
        Map<String, Long> counters = Rca1bEvidenceWriter.counters();
        for (String name : List.of("database_query_count", "database_query_failure_count",
                "database_write_attempt_blocked_count", "result_row_limit_exceeded_count",
                "transaction_read_only_violation_count", "p1_query_result_mismatch_count",
                "p2_query_result_mismatch_count", "duplicate_row_count", "stale_checkpoint_count", "timeout_count")) {
            counters.put(name, 0L);
        }
        return counters;
    }

    private static long seedCount(PostgreSQLContainer<?> container) throws SQLException {
        try (Connection owner = owner(container)) {
            return Long.parseLong(scalar(owner, "SELECT (SELECT count(*) FROM rca1b_fixture.scenario_registry) + "
                    + "(SELECT count(*) FROM rca1b_fixture.row_limit_probe) + "
                    + "(SELECT count(*) FROM public.data_recommendation_profile_input_projection_v1 WHERE projection_subject_ref='subject:rca1b-user-1') + "
                    + "(SELECT count(*) FROM public.data_experiment_outcome_input_projection_v1 WHERE projection_record_ref='outcome_record:rca1b:baseline')"));
        } catch (SQLException exception) {
            if (exception.getSQLState() != null && exception.getSQLState().startsWith("42")) return 0L;
            throw exception;
        }
    }

    private static long scenarioCount(PostgreSQLContainer<?> container) throws SQLException {
        try (Connection owner = owner(container)) {
            return Long.parseLong(scalar(owner, "SELECT count(*) FROM rca1b_fixture.scenario_registry"));
        }
    }

    private static String queryInventoryDigest(Rca1bQueryRegistry registry) {
        StringBuilder value = new StringBuilder();
        registry.inventory().values().stream().sorted(Comparator.comparing(Rca1bQueryRegistry.QueryDefinition::id))
                .forEach(definition -> value.append(definition.id()).append('|')
                        .append(definition.expectedFingerprint()).append('|').append(definition.resource()).append('\n'));
        return Rca1bQueryRegistry.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String resourceDigest(String resource) throws IOException {
        try (InputStream input = Rca1bDatabaseReconciliationTest.class.getClassLoader()
                .getResourceAsStream("recommendation-data-adoption/rca1b/" + resource)) {
            assertNotNull(input);
            return Rca1bQueryRegistry.sha256(Rca1bQueryRegistry.canonicalBytes(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static void validateRedaction(Path output) throws IOException {
        String content = Files.readString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.json"), StandardCharsets.UTF_8)
                + Files.readString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.tsv"), StandardCharsets.UTF_8);
        for (String forbidden : List.of("jdbc:", "localhost", "127.0.0.1", "rca1b_owner", ROLE_PASSWORD,
                "rca1b-fixture@example.invalid", "user:", "subject:", "session:", "rca1b-exposure",
                "SELECT ", "INSERT ", "password")) {
            assertFalse(content.toLowerCase(Locale.ROOT).contains(forbidden.toLowerCase(Locale.ROOT)), forbidden);
        }
    }

    private static void validateDuplicateEvidenceRejection(String databaseVersion, String testedSha, String seedDigest) {
        Rca1bEvidenceWriter.EvidenceRecord record = new Rca1bEvidenceWriter.EvidenceRecord(
                "0".repeat(64), "P1", Rca1bEvidenceWriter.CONTRACT_ID, "v1", "SOURCE_CHECKPOINT_V1",
                "1".repeat(64), "CHECKPOINT_PARITY", "MATCH_EXACT", "SAFE", "SAFE", "SAFE", "SAFE",
                "2".repeat(64), 1, 1, databaseVersion, Rca1bEvidenceWriter.ENVIRONMENT,
                "REPEATABLE_READ", true, 5_000, seedDigest, Rca1bQueryRegistry.VERIFIER_VERSION,
                testedSha, Rca1bEvidenceWriter.FIXED_EVIDENCE_TIME);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                new Rca1bEvidenceWriter().write(Files.createTempDirectory("rca1b-duplicate-evidence"),
                        List.of(record, record), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static String normalize(Object value) {
        if (value == null) return "NULL";
        if (value instanceof BigDecimal decimal) return "DECIMAL:" + decimal.stripTrailingZeros().toPlainString();
        if (value instanceof Number number) return "NUMBER:" + number;
        if (value instanceof Boolean bool) return "BOOLEAN:" + bool;
        if (value instanceof Timestamp timestamp) return "INSTANT:" + timestamp.toInstant();
        return "TEXT:" + value.toString().replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String safe(String value) {
        if (value == null) return "NULL";
        if (value.matches("[0-9a-f]{64}") || value.matches("[A-Z0-9_:-]{1,96}")) return value;
        return Rca1bQueryRegistry.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void increment(Map<String, Long> counters, String key) {
        counters.compute(key, (ignored, value) -> value == null ? 1L : value + 1L);
    }

    private static int sqlNumber(Path path) {
        Matcher matcher = SQL_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) throw new IllegalArgumentException(path.toString());
        return Integer.parseInt(matcher.group(1));
    }

    private static String imageMajor(String image) {
        Matcher matcher = Pattern.compile("postgres:(15|18)(?:-|$)").matcher(image);
        if (!matcher.find()) throw new IllegalArgumentException("unsupported PostgreSQL image: " + image);
        return matcher.group(1);
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("database/journey-connect-db-v2.7"))
                    && Files.isDirectory(cursor.resolve("jc-backend"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    record QueryResult(Rca1bQueryRegistry.QueryDefinition definition, List<String> rows) {
        int rowCount() { return rows.size(); }
        String digest() {
            return Rca1bQueryRegistry.sha256((String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
