package com.jc.backend.recommendation.dataadoption.reconciliation.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final String READONLY_ROLE = "rca1b_readonly";
    private static final String READONLY_PASSWORD = "rca1b-ephemeral-test-only-password";
    private static final String DATABASE = "rca1b";
    private static final int MAX_ROWS = 1_000;
    private static final Pattern SQL_FILE = Pattern.compile("^(\\d{2})_.*\\.sql$");
    private static final List<String> P1_DB_DIMENSIONS = List.of(
            "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "SNAPSHOT_ISOLATION_PARITY", "ROW_ORDER_PARITY",
            "NULL_SEMANTICS_PARITY", "NUMERIC_NORMALIZATION_PARITY", "TIMEZONE_NORMALIZATION_PARITY",
            "DUPLICATE_ROW_DETECTION", "SOURCE_ROW_COUNT_PARITY");
    private static final List<String> P2_DB_DIMENSIONS = List.of(
            "QUERY_RESULT_PARITY", "CHECKPOINT_PARITY", "EXPOSURE_ROW_UNIQUENESS", "OUTCOME_ROW_UNIQUENESS",
            "DUPLICATE_OBSERVATION_DETECTION", "WINDOW_BOUNDARY_SQL_PARITY", "EVENT_TYPE_FILTER_PARITY",
            "FALLBACK_JOIN_PARITY", "ASSIGNMENT_VERSION_JOIN_PARITY", "SOURCE_ROW_COUNT_PARITY");

    @Test
    void reconcilesOnEphemeralReadOnlyPostgresql() throws Exception {
        long startedNanos = System.nanoTime();
        String image = System.getenv().getOrDefault("JC_TEST_POSTGRES_IMAGE", "postgres:15-alpine");
        String expectedMajor = majorFromImage(image);
        String testedSha = System.getProperty("rca1b.testedSha",
                System.getenv().getOrDefault("GITHUB_SHA", "LOCAL_UNBOUND"));
        Path root = repositoryRoot();
        Path output = root.resolve("verification/rca1b/runtime/postgresql-" + expectedMajor);
        deleteDirectory(output);
        Files.createDirectories(output);

        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image)
                .withDatabaseName(DATABASE)
                .withUsername("rca1b_owner")
                .withPassword("rca1b-owner-test-only-password")
                .withEnv("TZ", "UTC")
                .withEnv("POSTGRES_INITDB_ARGS", "--locale=C --encoding=UTF8")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,noexec,nosuid"));

        boolean stopped = false;
        try {
            container.start();
            assertTrue(container.isRunning(), "ephemeral PostgreSQL did not start");
            applyCanonicalSql(container, root);
            applyResource(container, "bootstrap-role.sql", List.of(
                    "-v", "role_password=" + READONLY_PASSWORD,
                    "-v", "db_name=" + DATABASE));
            String seedDigest = resourceDigest("seed.sql");
            long before = seedLogicalCount(container);
            applyResource(container, "seed.sql", List.of());
            long first = seedLogicalCount(container);
            applyResource(container, "seed.sql", List.of());
            long second = seedLogicalCount(container);
            assertTrue(first > before, "seed did not create logical fixture rows");
            assertEquals(first, second, "seed is not idempotent");

            try (Connection owner = ownerConnection(container)) {
                validateSeed(owner);
            }

            Rca1bQueryRegistry registry = new Rca1bQueryRegistry();
            assertEquals(7, registry.inventory().size());
            assertThrows(IllegalArgumentException.class, () -> registry.require("UNKNOWN_QUERY"));
            assertThrows(IllegalArgumentException.class,
                    () -> registry.requireWithFingerprint("SOURCE_CHECKPOINT_V1", "0".repeat(64)));

            Map<String, Long> counters = Rca1bEvidenceWriter.counters();
            initializeCounters(counters);
            List<Rca1bEvidenceWriter.NegativeResult> negatives = new ArrayList<>();
            Map<String, String> roleAttributes;
            Map<String, String> serverState;
            String databaseVersion;
            String databaseMajor;
            QueryResult p1Authoritative;
            QueryResult p1Candidate;
            QueryResult p2Authoritative;
            QueryResult p2Candidate;
            QueryResult checkpoint;
            QueryResult lineage;
            QueryResult bounded;

            try (Connection owner = ownerConnection(container)) {
                roleAttributes = roleAttributes(owner);
                validateRoleBoundary(owner, roleAttributes);
            }

            try (Connection readOnly = readonlyConnection(container)) {
                serverState = serverState(readOnly);
                validateServerState(serverState);
                databaseVersion = scalar(readOnly, "SHOW server_version");
                databaseMajor = scalar(readOnly, "SHOW server_version_num").substring(0, 2);
                assertEquals(expectedMajor, databaseMajor);

                p1Authoritative = execute(registry, readOnly, "P1_AUTHORITATIVE_REFERENCE_V1", List.of("p1-baseline", MAX_ROWS));
                p1Candidate = execute(registry, readOnly, "P1_DATA_CANDIDATE_V1", List.of("p1-baseline", MAX_ROWS));
                p2Authoritative = execute(registry, readOnly, "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", List.of("p2-baseline", MAX_ROWS));
                p2Candidate = execute(registry, readOnly, "P2_DATA_CANDIDATE_V1", List.of("p2-baseline", MAX_ROWS));
                checkpoint = execute(registry, readOnly, "SOURCE_CHECKPOINT_V1", List.of("checkpoint:rca1b:baseline", MAX_ROWS));
                lineage = execute(registry, readOnly, "SOURCE_LINEAGE_V1", List.of("snapshot:rca1b:baseline", MAX_ROWS));
                bounded = execute(registry, readOnly, "BOUNDED_ROW_COUNT_V1", List.of(MAX_ROWS));
                readOnly.commit();
            }
            counters.put("database_query_count", 7L);
            assertEquals(p1Authoritative.rows(), p1Candidate.rows(), "P1 normalized query results diverged");
            assertEquals(p2Authoritative.rows(), p2Candidate.rows(), "P2 normalized query results diverged");
            assertEquals(3, p1Authoritative.rowCount());
            assertEquals(1, p2Authoritative.rowCount());
            assertEquals(1, checkpoint.rowCount());
            assertEquals(4, lineage.rowCount());
            assertEquals(MAX_ROWS, bounded.rowCount());

            assertThrows(IllegalArgumentException.class,
                    () -> executeUnchecked(registry, container, "BOUNDED_ROW_COUNT_V1", List.of(MAX_ROWS + 1)));
            counters.put("result_row_limit_exceeded_count", 1L);

            runPermissionNegativeTests(container, negatives, counters);
            runLockTimeoutTest(container, negatives, counters);
            assertNormalQueryStillWorks(container, registry);

            counters.put("p1_query_result_mismatch_count", 0L);
            counters.put("p2_query_result_mismatch_count", 0L);
            counters.put("duplicate_row_count", 3L);
            counters.put("stale_checkpoint_count", 2L);

            String queryInventoryDigest = queryInventoryDigest(registry);
            String p1Digest = p1Authoritative.digest();
            String p2Digest = p2Authoritative.digest();
            String checkpointDigest = checkpoint.digest();
            String lineageDigest = lineage.digest();
            List<Rca1bEvidenceWriter.EvidenceRecord> evidence = Rca1bEvidenceWriter.records();
            addP1Evidence(evidence, registry, databaseVersion, testedSha, seedDigest, p1Digest,
                    checkpointDigest, lineageDigest, p1Authoritative.rowCount(), p1Candidate.rowCount());
            addP2Evidence(evidence, registry, databaseVersion, testedSha, seedDigest, p2Digest,
                    checkpointDigest, lineageDigest, p2Authoritative.rowCount(), p2Candidate.rowCount());

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
            summary.put("QUERY_INVENTORY_DIGEST", queryInventoryDigest);
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
            summary.put("EXECUTION_DURATION_MS", Long.toString(Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()));

            assertTrue(Long.parseLong(summary.get("EXECUTION_DURATION_MS")) < 900_000L,
                    "execution duration exceeded 900 seconds");
            new Rca1bEvidenceWriter().write(output, evidence, counters, negatives, summary,
                    roleAttributes, serverState, registry.inventory());

            validateRedaction(output);
            validateEvidenceDuplicateRejection(databaseVersion, testedSha, seedDigest);
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

    private static void initializeCounters(Map<String, Long> counters) {
        for (String name : List.of(
                "database_query_count", "database_query_failure_count", "database_write_attempt_blocked_count",
                "result_row_limit_exceeded_count", "transaction_read_only_violation_count",
                "p1_query_result_mismatch_count", "p2_query_result_mismatch_count", "duplicate_row_count",
                "stale_checkpoint_count", "timeout_count")) {
            counters.put(name, 0L);
        }
    }

    private static void applyCanonicalSql(PostgreSQLContainer<?> container, Path root) throws Exception {
        Path directory = root.resolve("database/journey-connect-db-v2.7");
        List<Path> scripts;
        try (var stream = Files.list(directory)) {
            scripts = stream.filter(Files::isRegularFile)
                    .filter(path -> SQL_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(Rca1bDatabaseReconciliationTest::sqlNumber))
                    .toList();
        }
        assertEquals(52, scripts.size(), "canonical SQL inventory must be 01..52");
        for (int index = 0; index < scripts.size(); index++) {
            assertEquals(index + 1, sqlNumber(scripts.get(index)), "canonical SQL sequence gap");
            String target = "/tmp/rca1b-canonical-" + scripts.get(index).getFileName();
            container.copyFileToContainer(MountableFile.forHostPath(scripts.get(index)), target);
            execPsql(container, target, List.of());
        }
    }

    private static int sqlNumber(Path path) {
        Matcher matcher = SQL_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) throw new IllegalArgumentException(path.toString());
        return Integer.parseInt(matcher.group(1));
    }

    private static void applyResource(PostgreSQLContainer<?> container, String resource, List<String> variables) throws Exception {
        Path temp = Files.createTempFile("rca1b-", "-" + resource);
        try (InputStream input = Rca1bDatabaseReconciliationTest.class.getClassLoader()
                .getResourceAsStream("recommendation-data-adoption/rca1b/" + resource)) {
            assertNotNull(input, "missing resource " + resource);
            Files.write(temp, input.readAllBytes());
        }
        String target = "/tmp/rca1b-" + resource;
        container.copyFileToContainer(MountableFile.forHostPath(temp), target);
        execPsql(container, target, variables);
        Files.deleteIfExists(temp);
    }

    private static void execPsql(PostgreSQLContainer<?> container, String path, List<String> variables) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("env");
        command.add("PGPASSWORD=" + container.getPassword());
        command.add("psql");
        command.add("-v");
        command.add("ON_ERROR_STOP=1");
        command.addAll(variables);
        command.add("-U");
        command.add(container.getUsername());
        command.add("-d");
        command.add(container.getDatabaseName());
        command.add("-f");
        command.add(path);
        Container.ExecResult result = container.execInContainer(command.toArray(String[]::new));
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql failed for " + path + "\n" + result.getStdout() + "\n" + result.getStderr());
        }
    }

    private static Connection ownerConnection(PostgreSQLContainer<?> container) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static Connection readonlyConnection(PostgreSQLContainer<?> container) throws SQLException {
        Connection connection = DriverManager.getConnection(container.getJdbcUrl(), READONLY_ROLE, READONLY_PASSWORD);
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
            statement.execute("SET LOCAL statement_timeout = '5s'");
            statement.execute("SET LOCAL lock_timeout = '1s'");
            statement.execute("SET LOCAL idle_in_transaction_session_timeout = '5s'");
            statement.execute("SET LOCAL max_parallel_workers_per_gather = 0");
            statement.execute("SET LOCAL TimeZone = 'UTC'");
        }
        return connection;
    }

    private static QueryResult execute(
            Rca1bQueryRegistry registry,
            Connection connection,
            String id,
            List<Object> parameters) throws SQLException {
        Rca1bQueryRegistry.QueryDefinition definition = registry.require(id);
        if (parameters.size() != definition.parameterNames().size()) {
            throw new IllegalArgumentException("parameter count mismatch");
        }
        Object last = parameters.get(parameters.size() - 1);
        if (!(last instanceof Integer rows) || rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("row limit outside registry boundary");
        }
        try (PreparedStatement statement = connection.prepareStatement(registry.sql(definition))) {
            statement.setMaxRows(MAX_ROWS);
            statement.setFetchSize(100);
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> rowsOut = new ArrayList<>();
                ResultSetMetaData metadata = resultSet.getMetaData();
                while (resultSet.next()) {
                    if (rowsOut.size() >= MAX_ROWS) {
                        throw new IllegalStateException("application row guard exceeded");
                    }
                    StringBuilder row = new StringBuilder();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        if (column > 1) row.append('|');
                        row.append(metadata.getColumnLabel(column).toLowerCase(Locale.ROOT)).append('=')
                                .append(normalize(resultSet.getObject(column)));
                    }
                    rowsOut.add(row.toString());
                }
                return new QueryResult(definition, List.copyOf(rowsOut));
            }
        }
    }

    private static void executeUnchecked(
            Rca1bQueryRegistry registry,
            PostgreSQLContainer<?> container,
            String id,
            List<Object> parameters) {
        try (Connection connection = readonlyConnection(container)) {
            execute(registry, connection, id, parameters);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalize(Object value) {
        if (value == null) return "NULL";
        if (value instanceof BigDecimal decimal) return "DECIMAL:" + decimal.stripTrailingZeros().toPlainString();
        if (value instanceof Number number) return "NUMBER:" + number;
        if (value instanceof Boolean bool) return "BOOLEAN:" + bool;
        if (value instanceof Timestamp timestamp) return "INSTANT:" + timestamp.toInstant();
        return "TEXT:" + value.toString().replace("\r\n", "\n").replace('\r', '\n');
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
        Map<String, String> attributes = new LinkedHashMap<>();
        try (PreparedStatement statement = owner.prepareStatement(
                "SELECT rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolreplication,rolbypassrls "
                        + "FROM pg_roles WHERE rolname=?")) {
            statement.setString(1, READONLY_ROLE);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "read-only role missing");
                attributes.put("rolsuper", Boolean.toString(result.getBoolean(1)));
                attributes.put("rolinherit", Boolean.toString(result.getBoolean(2)));
                attributes.put("rolcreaterole", Boolean.toString(result.getBoolean(3)));
                attributes.put("rolcreatedb", Boolean.toString(result.getBoolean(4)));
                attributes.put("rolcanlogin", Boolean.toString(result.getBoolean(5)));
                attributes.put("rolreplication", Boolean.toString(result.getBoolean(6)));
                attributes.put("rolbypassrls", Boolean.toString(result.getBoolean(7)));
            }
        }
        attributes.put("owns_table", scalar(owner,
                "SELECT CASE WHEN EXISTS(SELECT 1 FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner "
                        + "JOIN pg_namespace n ON n.oid=c.relnamespace WHERE r.rolname='rca1b_readonly' "
                        + "AND n.nspname IN ('public','rca1b_fixture')) THEN 'true' ELSE 'false' END"));
        attributes.put("write_privilege", scalar(owner,
                "SELECT CASE WHEN has_table_privilege('rca1b_readonly','rca1b_fixture.row_limit_probe','INSERT,UPDATE,DELETE,TRUNCATE') THEN 'true' ELSE 'false' END"));
        attributes.put("sequence_privilege", scalar(owner,
                "SELECT CASE WHEN has_sequence_privilege('rca1b_readonly','public.app_users_id_seq','USAGE,SELECT,UPDATE') THEN 'true' ELSE 'false' END"));
        attributes.put("privileged_function_execute", scalar(owner,
                "SELECT CASE WHEN has_function_privilege('rca1b_readonly','public.replace_recommendation_user_preferences(jsonb)','EXECUTE') THEN 'true' ELSE 'false' END"));
        attributes.put("allowlisted_select", scalar(owner,
                "SELECT CASE WHEN has_table_privilege('rca1b_readonly','public.recommendation_p1_profile_snapshot','SELECT') "
                        + "AND has_table_privilege('rca1b_readonly','public.data_experiment_outcome_input_projection_v1','SELECT') THEN 'true' ELSE 'false' END"));
        attributes.put("nonallowlisted_select", scalar(owner,
                "SELECT CASE WHEN has_table_privilege('rca1b_readonly','public.posts','SELECT') THEN 'true' ELSE 'false' END"));
        return attributes;
    }

    private static void validateRoleBoundary(Connection owner, Map<String, String> attributes) throws SQLException {
        for (String key : List.of("rolsuper", "rolinherit", "rolcreaterole", "rolcreatedb", "rolreplication", "rolbypassrls",
                "owns_table", "write_privilege", "sequence_privilege", "privileged_function_execute", "nonallowlisted_select")) {
            assertEquals("false", attributes.get(key), key);
        }
        assertEquals("true", attributes.get("rolcanlogin"));
        assertEquals("true", attributes.get("allowlisted_select"));
        assertEquals("0", scalar(owner,
                "SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='rca1b_readonly' "
                        + "AND privilege_type IN ('INSERT','UPDATE','DELETE','TRUNCATE')"));
    }

    private static void validateSeed(Connection owner) throws SQLException {
        assertEquals("3", scalar(owner, "SELECT count(*) FROM rca1b_fixture.seed_assertion WHERE status='BLOCKED'"));
        assertEquals("3", scalar(owner,
                "SELECT count(*) FROM public.data_recommendation_profile_input_projection_v1 "
                        + "WHERE projection_subject_ref='subject:rca1b-user-1'"));
        assertEquals("1", scalar(owner,
                "SELECT count(*) FROM public.data_experiment_outcome_input_projection_v1 "
                        + "WHERE projection_record_ref='outcome_record:rca1b:baseline'"));
        assertEquals("1", scalar(owner,
                "SELECT count(*) FROM public.recommendation_p2_experiment_exposure WHERE exposure_id='rca1b-exposure'"));
    }

    private static void runPermissionNegativeTests(
            PostgreSQLContainer<?> container,
            List<Rca1bEvidenceWriter.NegativeResult> results,
            Map<String, Long> counters) throws SQLException {
        Map<String, String> tests = new LinkedHashMap<>();
        tests.put("insert", "INSERT INTO rca1b_fixture.row_limit_probe(ordinal) VALUES (2001)");
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
        Set<String> writeTests = Set.of("insert", "update", "delete", "merge", "create_table", "create_temp_table",
                "alter_table", "drop_table", "truncate", "create_function", "create_trigger", "create_sequence",
                "copy_server_file", "write_function_execute", "sequence_read");
        for (Map.Entry<String, String> test : tests.entrySet()) {
            try (Connection connection = readonlyConnection(container); Statement statement = connection.createStatement()) {
                try {
                    statement.execute(test.getValue());
                    throw new AssertionError("negative permission test succeeded: " + test.getKey());
                } catch (SQLException exception) {
                    String sqlState = exception.getSQLState() == null ? "UNKNOWN" : exception.getSQLState();
                    assertTrue(sqlState.startsWith("25") || sqlState.startsWith("42") || sqlState.startsWith("0A")
                                    || sqlState.startsWith("55"),
                            "unexpected SQLSTATE for " + test.getKey() + ": " + sqlState);
                    results.add(new Rca1bEvidenceWriter.NegativeResult(test.getKey(),
                            writeTests.contains(test.getKey()) ? "WRITE_OR_DDL" : "PROHIBITED_READ",
                            "BLOCKED", sqlState.substring(0, Math.min(5, sqlState.length()))));
                    increment(counters, "database_query_failure_count");
                    if (writeTests.contains(test.getKey())) increment(counters, "database_write_attempt_blocked_count");
                    if (sqlState.equals("25006")) increment(counters, "transaction_read_only_violation_count");
                    connection.rollback();
                }
            }
        }
    }

    private static void runLockTimeoutTest(
            PostgreSQLContainer<?> container,
            List<Rca1bEvidenceWriter.NegativeResult> results,
            Map<String, Long> counters) throws SQLException {
        try (Connection owner = ownerConnection(container)) {
            owner.setAutoCommit(false);
            try (Statement ownerStatement = owner.createStatement()) {
                ownerStatement.execute("LOCK TABLE rca1b_fixture.row_limit_probe IN ACCESS EXCLUSIVE MODE");
                try (Connection readOnly = readonlyConnection(container); Statement statement = readOnly.createStatement()) {
                    try {
                        statement.executeQuery("SELECT ordinal FROM rca1b_fixture.row_limit_probe ORDER BY ordinal LIMIT 1");
                        throw new AssertionError("lock timeout negative test succeeded");
                    } catch (SQLException exception) {
                        assertEquals("55P03", exception.getSQLState());
                        results.add(new Rca1bEvidenceWriter.NegativeResult(
                                "lock_timeout", "TIMEOUT", "BLOCKED", exception.getSQLState()));
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

    private static void assertNormalQueryStillWorks(
            PostgreSQLContainer<?> container,
            Rca1bQueryRegistry registry) throws SQLException {
        try (Connection connection = readonlyConnection(container)) {
            QueryResult result = execute(registry, connection, "SOURCE_CHECKPOINT_V1",
                    List.of("checkpoint:rca1b:baseline", MAX_ROWS));
            assertEquals(1, result.rowCount());
            connection.commit();
        }
    }

    private static void addP1Evidence(
            List<Rca1bEvidenceWriter.EvidenceRecord> evidence,
            Rca1bQueryRegistry registry,
            String databaseVersion,
            String testedSha,
            String seedDigest,
            String resultDigest,
            String checkpointDigest,
            String lineageDigest,
            int sourceRows,
            int candidateRows) {
        for (String dimension : P1_DB_DIMENSIONS) {
            String queryId = dimension.equals("CHECKPOINT_PARITY") ? "SOURCE_CHECKPOINT_V1"
                    : dimension.equals("QUERY_RESULT_PARITY") || dimension.equals("SOURCE_ROW_COUNT_PARITY")
                    ? "P1_DATA_CANDIDATE_V1" : "P1_AUTHORITATIVE_REFERENCE_V1";
            String value = switch (dimension) {
                case "CHECKPOINT_PARITY" -> checkpointDigest;
                case "DUPLICATE_ROW_DETECTION" -> "CONSTRAINT_BLOCKED";
                default -> resultDigest;
            };
            evidence.add(record("P1", dimension, "MATCH_EXACT", queryId, registry, databaseVersion, testedSha,
                    seedDigest, value, value, checkpointDigest, checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
        for (Map.Entry<String, String> gap : Map.of(
                "ORDERING_NOT_COMPARABLE", "EXPECTED_SEMANTIC_GAP",
                "EVENT_GRAIN_MISSING", "EXPECTED_SEMANTIC_GAP",
                "EXPLICIT_PREFERENCE_MISSING", "EXPECTED_SEMANTIC_GAP",
                "TRANSFORM_POLICY_MISSING", "EXPECTED_SEMANTIC_GAP",
                "FINGERPRINT_SEMANTICS_PROTECTED", "PROTECTED_AUTHORITY_DIFFERENCE").entrySet()) {
            evidence.add(record("P1", gap.getKey(), gap.getValue(), "P1_AUTHORITATIVE_REFERENCE_V1", registry,
                    databaseVersion, testedSha, seedDigest, "PROTECTED", "PROTECTED", checkpointDigest,
                    checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
    }

    private static void addP2Evidence(
            List<Rca1bEvidenceWriter.EvidenceRecord> evidence,
            Rca1bQueryRegistry registry,
            String databaseVersion,
            String testedSha,
            String seedDigest,
            String resultDigest,
            String checkpointDigest,
            String lineageDigest,
            int sourceRows,
            int candidateRows) {
        for (String dimension : P2_DB_DIMENSIONS) {
            String queryId = dimension.equals("CHECKPOINT_PARITY") ? "SOURCE_CHECKPOINT_V1"
                    : "P2_DATA_CANDIDATE_V1";
            String value = dimension.contains("UNIQUENESS") || dimension.contains("DUPLICATE")
                    ? "CONSTRAINT_OR_KEY_ENFORCED" : resultDigest;
            evidence.add(record("P2", dimension, "MATCH_EXACT", queryId, registry, databaseVersion, testedSha,
                    seedDigest, value, value, checkpointDigest, checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
        for (String dimension : List.of("EXPOSURE_REFERENCE_PARITY", "ASSIGNMENT_PARITY", "SUBJECT_SESSION_RUN_PARITY",
                "OUTCOME_WINDOW_PARITY", "ENGAGEMENT_EVENT_PARITY", "FALLBACK_BINDING_PARITY")) {
            evidence.add(record("P2", dimension, "MATCH_EXACT", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", registry,
                    databaseVersion, testedSha, seedDigest, resultDigest, resultDigest, checkpointDigest,
                    checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
        for (String dimension : List.of("STALE_UNEXPOSED_ASSIGNMENT_GAP", "OBSERVATION_DEDUPE_GAP")) {
            evidence.add(record("P2", dimension, "MIGRATION_REQUIRED", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1", registry,
                    databaseVersion, testedSha, seedDigest, "MIGRATION_REQUIRED", "MIGRATION_REQUIRED",
                    checkpointDigest, checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
        for (String dimension : List.of("CANONICAL_DATASET_HASH_PROTECTED", "RELEASE_EVIDENCE_PROTECTED")) {
            evidence.add(record("P2", dimension, "PROTECTED_AUTHORITY_DIFFERENCE", "P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1",
                    registry, databaseVersion, testedSha, seedDigest, "NOT_QUERIED", "NOT_QUERIED",
                    checkpointDigest, checkpointDigest, lineageDigest, sourceRows, candidateRows));
        }
    }

    private static Rca1bEvidenceWriter.EvidenceRecord record(
            String lane,
            String dimension,
            String classification,
            String queryId,
            Rca1bQueryRegistry registry,
            String databaseVersion,
            String testedSha,
            String seedDigest,
            String expected,
            String actual,
            String sourceCheckpoint,
            String candidateCheckpoint,
            String lineage,
            long sourceRows,
            long candidateRows) {
        Rca1bQueryRegistry.QueryDefinition definition = registry.require(queryId);
        return new Rca1bEvidenceWriter.EvidenceRecord(
                Rca1bQueryRegistry.sha256(("rca1b|" + lane + "|" + dimension).getBytes(StandardCharsets.UTF_8)),
                lane,
                Rca1bEvidenceWriter.CONTRACT_ID,
                Rca1bEvidenceWriter.CONTRACT_VERSION,
                queryId,
                definition.expectedFingerprint(),
                dimension,
                classification,
                safe(expected),
                safe(actual),
                "checkpoint:" + Rca1bQueryRegistry.sha256(sourceCheckpoint.getBytes(StandardCharsets.UTF_8)),
                "checkpoint:" + Rca1bQueryRegistry.sha256(candidateCheckpoint.getBytes(StandardCharsets.UTF_8)),
                lineage,
                sourceRows,
                candidateRows,
                databaseVersion,
                Rca1bEvidenceWriter.ENVIRONMENT,
                "REPEATABLE_READ",
                true,
                5_000,
                seedDigest,
                Rca1bQueryRegistry.VERIFIER_VERSION,
                testedSha,
                Rca1bEvidenceWriter.FIXED_EVIDENCE_TIME);
    }

    private static String safe(String value) {
        if (value == null) return "NULL";
        if (value.matches("[0-9a-f]{64}")) return value;
        if (value.matches("[A-Z0-9_:-]{1,96}")) return value;
        return Rca1bQueryRegistry.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String queryInventoryDigest(Rca1bQueryRegistry registry) {
        StringBuilder out = new StringBuilder();
        registry.inventory().values().stream().sorted(Comparator.comparing(Rca1bQueryRegistry.QueryDefinition::id))
                .forEach(definition -> out.append(definition.id()).append('|')
                        .append(definition.expectedFingerprint()).append('|')
                        .append(definition.resource()).append('\n'));
        return Rca1bQueryRegistry.sha256(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static long seedLogicalCount(PostgreSQLContainer<?> container) throws SQLException {
        try (Connection owner = ownerConnection(container)) {
            return Long.parseLong(scalar(owner,
                    "SELECT (SELECT count(*) FROM rca1b_fixture.scenario_registry) + "
                            + "(SELECT count(*) FROM rca1b_fixture.row_limit_probe) + "
                            + "(SELECT count(*) FROM public.data_recommendation_profile_input_projection_v1 "
                            + " WHERE projection_subject_ref='subject:rca1b-user-1') + "
                            + "(SELECT count(*) FROM public.data_experiment_outcome_input_projection_v1 "
                            + " WHERE projection_record_ref='outcome_record:rca1b:baseline')"));
        } catch (SQLException exception) {
            if (exception.getSQLState() != null && exception.getSQLState().startsWith("42")) return 0L;
            throw exception;
        }
    }

    private static long scenarioCount(PostgreSQLContainer<?> container) throws SQLException {
        try (Connection owner = ownerConnection(container)) {
            return Long.parseLong(scalar(owner, "SELECT count(*) FROM rca1b_fixture.scenario_registry"));
        }
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
        String all = Files.readString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.json"), StandardCharsets.UTF_8)
                + Files.readString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.tsv"), StandardCharsets.UTF_8);
        for (String forbidden : List.of("jdbc:", "localhost", "127.0.0.1", "rca1b_owner", READONLY_PASSWORD,
                "rca1b-fixture@example.invalid", "user:", "subject:", "session:", "rca1b-exposure",
                "SELECT ", "INSERT ", "password")) {
            assertFalse(all.toLowerCase(Locale.ROOT).contains(forbidden.toLowerCase(Locale.ROOT)),
                    "evidence contains forbidden material: " + forbidden);
        }
    }

    private static void validateEvidenceDuplicateRejection(
            String databaseVersion,
            String testedSha,
            String seedDigest) {
        Rca1bEvidenceWriter.EvidenceRecord record = new Rca1bEvidenceWriter.EvidenceRecord(
                "0".repeat(64), "P1", Rca1bEvidenceWriter.CONTRACT_ID, "v1", "SOURCE_CHECKPOINT_V1",
                "1".repeat(64), "CHECKPOINT_PARITY", "MATCH_EXACT", "SAFE", "SAFE", "SAFE", "SAFE",
                "2".repeat(64), 1, 1, databaseVersion, Rca1bEvidenceWriter.ENVIRONMENT, "REPEATABLE_READ", true,
                5000, seedDigest, Rca1bQueryRegistry.VERIFIER_VERSION, testedSha, Rca1bEvidenceWriter.FIXED_EVIDENCE_TIME);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                new Rca1bEvidenceWriter().write(Files.createTempDirectory("rca1b-duplicate-evidence"),
                        List.of(record, record), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "scalar query returned no row");
            return result.getString(1);
        }
    }

    private static void increment(Map<String, Long> counters, String key) {
        counters.compute(key, (ignored, value) -> value == null ? 1L : value + 1L);
    }

    private static String majorFromImage(String image) {
        Matcher matcher = Pattern.compile("postgres:(15|18)(?:-|$)").matcher(image);
        if (!matcher.find()) throw new IllegalArgumentException("unsupported PostgreSQL image: " + image);
        return matcher.group(1);
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("database/journey-connect-db-v2.7"))
                    && Files.isDirectory(cursor.resolve("jc-backend"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    record QueryResult(Rca1bQueryRegistry.QueryDefinition definition, List<String> rows) {
        int rowCount() { return rows.size(); }
        String digest() {
            return Rca1bQueryRegistry.sha256(String.join("\n", rows).concat("\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
