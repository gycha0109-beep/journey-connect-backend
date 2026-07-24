package com.jc.backend.recommendation.dataadoption.reconciliation.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Rca1bEvidenceWriter {
    static final Instant FIXED_EVIDENCE_TIME = Instant.parse("2026-07-24T00:00:00Z");
    static final String CONTRACT_ID = "recommendation-nonproduction-readonly-reconciliation-evidence-v1";
    static final String CONTRACT_VERSION = "v1";
    static final String ENVIRONMENT = "CI_EPHEMERAL_POSTGRESQL";
    static final String TSV_HEADER = String.join("\t",
            "hashedCaseId", "lane", "contractId", "contractVersion", "queryId", "queryFingerprint",
            "comparisonDimension", "classification", "normalizedExpected", "normalizedActual",
            "sourceCheckpoint", "candidateCheckpoint", "lineageFingerprint", "sourceRowCount",
            "candidateRowCount", "databaseVersion", "executionEnvironment", "transactionIsolation",
            "transactionReadOnly", "statementTimeoutMs", "seedDigest", "verifierVersion", "testedSha",
            "evidenceTimestamp");

    record EvidenceRecord(
            String hashedCaseId,
            String lane,
            String contractId,
            String contractVersion,
            String queryId,
            String queryFingerprint,
            String comparisonDimension,
            String classification,
            String normalizedExpected,
            String normalizedActual,
            String sourceCheckpoint,
            String candidateCheckpoint,
            String lineageFingerprint,
            long sourceRowCount,
            long candidateRowCount,
            String databaseVersion,
            String executionEnvironment,
            String transactionIsolation,
            boolean transactionReadOnly,
            int statementTimeoutMs,
            String seedDigest,
            String verifierVersion,
            String testedSha,
            Instant evidenceTimestamp) {}

    record NegativeResult(String testId, String category, String status, String sqlStateClass) {}

    void write(
            Path output,
            List<EvidenceRecord> records,
            Map<String, Long> counters,
            List<NegativeResult> negatives,
            Map<String, String> summary,
            Map<String, String> roleAttributes,
            Map<String, String> serverState,
            Map<String, Rca1bQueryRegistry.QueryDefinition> inventory) throws IOException {
        Files.createDirectories(output);
        List<EvidenceRecord> ordered = records.stream()
                .sorted(Comparator.comparing(EvidenceRecord::lane)
                        .thenComparing(EvidenceRecord::hashedCaseId)
                        .thenComparing(EvidenceRecord::comparisonDimension)
                        .thenComparing(EvidenceRecord::queryId)
                        .thenComparing(EvidenceRecord::databaseVersion))
                .toList();
        rejectDuplicates(ordered);
        Files.writeString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.tsv"), evidenceTsv(ordered), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_RECONCILIATION_EVIDENCE.json"), evidenceJson(ordered), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_VERIFICATION_COUNTERS.tsv"), mapTsv("counter", "value", counters), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_PERMISSION_NEGATIVE_RESULTS.tsv"), negativeTsv(negatives), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_EXECUTION_SUMMARY.tsv"), mapTsv("key", "value", summary), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_ROLE_ATTRIBUTES.tsv"), mapTsv("attribute", "value", roleAttributes), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_SERVER_STATE.tsv"), mapTsv("setting", "value", serverState), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_QUERY_INVENTORY.tsv"), queryInventory(inventory), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_CANONICAL_RESULT.json"), canonicalResult(ordered, counters, summary), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("RCA1B_REVIEW_PACKAGE.tsv"), reviewPackage(summary), StandardCharsets.UTF_8);
    }

    private static void rejectDuplicates(List<EvidenceRecord> records) {
        Set<String> keys = new LinkedHashSet<>();
        for (EvidenceRecord record : records) {
            String key = String.join("|", record.hashedCaseId(), record.lane(), record.comparisonDimension(),
                    record.queryId(), record.databaseVersion());
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate evidence key");
            }
        }
    }

    private static String evidenceTsv(List<EvidenceRecord> records) {
        StringBuilder out = new StringBuilder(TSV_HEADER).append('\n');
        for (EvidenceRecord record : records) {
            out.append(cell(record.hashedCaseId())).append('\t')
                    .append(record.lane()).append('\t')
                    .append(record.contractId()).append('\t')
                    .append(record.contractVersion()).append('\t')
                    .append(record.queryId()).append('\t')
                    .append(record.queryFingerprint()).append('\t')
                    .append(record.comparisonDimension()).append('\t')
                    .append(record.classification()).append('\t')
                    .append(cell(record.normalizedExpected())).append('\t')
                    .append(cell(record.normalizedActual())).append('\t')
                    .append(cell(record.sourceCheckpoint())).append('\t')
                    .append(cell(record.candidateCheckpoint())).append('\t')
                    .append(record.lineageFingerprint()).append('\t')
                    .append(record.sourceRowCount()).append('\t')
                    .append(record.candidateRowCount()).append('\t')
                    .append(cell(record.databaseVersion())).append('\t')
                    .append(record.executionEnvironment()).append('\t')
                    .append(record.transactionIsolation()).append('\t')
                    .append(record.transactionReadOnly()).append('\t')
                    .append(record.statementTimeoutMs()).append('\t')
                    .append(record.seedDigest()).append('\t')
                    .append(record.verifierVersion()).append('\t')
                    .append(record.testedSha()).append('\t')
                    .append(record.evidenceTimestamp()).append('\n');
        }
        return out.toString();
    }

    private static String evidenceJson(List<EvidenceRecord> records) {
        StringBuilder out = new StringBuilder("[\n");
        for (int index = 0; index < records.size(); index++) {
            EvidenceRecord record = records.get(index);
            if (index > 0) out.append(",\n");
            out.append("  {")
                    .append(field("hashedCaseId", record.hashedCaseId())).append(',')
                    .append(field("lane", record.lane())).append(',')
                    .append(field("contractId", record.contractId())).append(',')
                    .append(field("contractVersion", record.contractVersion())).append(',')
                    .append(field("queryId", record.queryId())).append(',')
                    .append(field("queryFingerprint", record.queryFingerprint())).append(',')
                    .append(field("comparisonDimension", record.comparisonDimension())).append(',')
                    .append(field("classification", record.classification())).append(',')
                    .append(field("normalizedExpected", record.normalizedExpected())).append(',')
                    .append(field("normalizedActual", record.normalizedActual())).append(',')
                    .append(field("sourceCheckpoint", record.sourceCheckpoint())).append(',')
                    .append(field("candidateCheckpoint", record.candidateCheckpoint())).append(',')
                    .append(field("lineageFingerprint", record.lineageFingerprint())).append(',')
                    .append("\"sourceRowCount\":").append(record.sourceRowCount()).append(',')
                    .append("\"candidateRowCount\":").append(record.candidateRowCount()).append(',')
                    .append(field("databaseVersion", record.databaseVersion())).append(',')
                    .append(field("executionEnvironment", record.executionEnvironment())).append(',')
                    .append(field("transactionIsolation", record.transactionIsolation())).append(',')
                    .append("\"transactionReadOnly\":").append(record.transactionReadOnly()).append(',')
                    .append("\"statementTimeoutMs\":").append(record.statementTimeoutMs()).append(',')
                    .append(field("seedDigest", record.seedDigest())).append(',')
                    .append(field("verifierVersion", record.verifierVersion())).append(',')
                    .append(field("testedSha", record.testedSha())).append(',')
                    .append(field("evidenceTimestamp", record.evidenceTimestamp().toString()))
                    .append('}');
        }
        return out.append("\n]\n").toString();
    }

    private static String canonicalResult(
            List<EvidenceRecord> records,
            Map<String, Long> counters,
            Map<String, String> summary) {
        StringBuilder material = new StringBuilder();
        for (EvidenceRecord record : records) {
            material.append(record.hashedCaseId()).append('|').append(record.lane()).append('|')
                    .append(record.queryId()).append('|').append(record.queryFingerprint()).append('|')
                    .append(record.comparisonDimension()).append('|').append(record.classification()).append('|')
                    .append(record.normalizedExpected()).append('|').append(record.normalizedActual()).append('|')
                    .append(record.sourceCheckpoint()).append('|').append(record.candidateCheckpoint()).append('|')
                    .append(record.lineageFingerprint()).append('|').append(record.sourceRowCount()).append('|')
                    .append(record.candidateRowCount()).append('\n');
        }
        counters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> material.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        String digest = Rca1bQueryRegistry.sha256(material.toString().getBytes(StandardCharsets.UTF_8));
        return "{\n"
                + "  \"contractId\": \"recommendation-nonproduction-readonly-reconciliation-v1\",\n"
                + "  \"p1Result\": \"" + json(summary.get("P1_RESULT")) + "\",\n"
                + "  \"p2Result\": \"" + json(summary.get("P2_RESULT")) + "\",\n"
                + "  \"seedDigest\": \"" + json(summary.get("SEED_DIGEST")) + "\",\n"
                + "  \"queryInventoryDigest\": \"" + json(summary.get("QUERY_INVENTORY_DIGEST")) + "\",\n"
                + "  \"canonicalEvidenceDigest\": \"" + digest + "\",\n"
                + "  \"readOnlyBoundary\": \"ENFORCED\",\n"
                + "  \"checkpointBoundary\": \"ENFORCED\",\n"
                + "  \"lineageBoundary\": \"ENFORCED\",\n"
                + "  \"identityBoundary\": \"ENFORCED\",\n"
                + "  \"currentAuthority\": \"UNCHANGED\",\n"
                + "  \"noAuthorityTransfer\": true\n"
                + "}\n";
    }

    private static String queryInventory(Map<String, Rca1bQueryRegistry.QueryDefinition> inventory) {
        StringBuilder out = new StringBuilder("queryId\tlane\tresource\tfingerprint\tmaxRows\torderKey\tparameters\n");
        inventory.values().stream().sorted(Comparator.comparing(Rca1bQueryRegistry.QueryDefinition::id))
                .forEach(definition -> out.append(definition.id()).append('\t')
                        .append(definition.lane()).append('\t')
                        .append(definition.resource()).append('\t')
                        .append(definition.expectedFingerprint()).append('\t')
                        .append(Rca1bQueryRegistry.MAX_ROWS).append('\t')
                        .append(cell(definition.deterministicOrderKey())).append('\t')
                        .append(String.join(",", definition.parameterNames())).append('\n'));
        return out.toString();
    }

    private static String negativeTsv(List<NegativeResult> results) {
        StringBuilder out = new StringBuilder("testId\tcategory\tstatus\tsqlStateClass\n");
        results.stream().sorted(Comparator.comparing(NegativeResult::testId))
                .forEach(result -> out.append(result.testId()).append('\t').append(result.category()).append('\t')
                        .append(result.status()).append('\t').append(result.sqlStateClass()).append('\n'));
        return out.toString();
    }

    private static String reviewPackage(Map<String, String> summary) {
        return "reviewer\tstatus\tpackage\n"
                + "Intelligence\tPENDING_USER_REVIEW\tP1 query inventory, dimensions, mismatches, checkpoint/lineage, exit recommendation\n"
                + "Reliability\tPENDING_USER_REVIEW\tP2 exposure/window/event/fallback, migration gaps, authority and evidence integrity\n"
                + "Operations\tPENDING_USER_REVIEW\tPostgreSQL version, network isolation, role/grants, read-only, limits and teardown\n"
                + "Privacy/Security\tPENDING_USER_REVIEW\tSynthetic identity, raw-data absence, credentials, retention and redaction\n"
                + "System Coordination\tPENDING_USER_REVIEW\twork-start/final-head, registry, SQL/source protection and no-transfer markers\n"
                + "implementation\tVERIFIED_NOT_APPROVED\t" + cell(summary.getOrDefault("TESTED_SHA", "UNKNOWN")) + "\n";
    }

    private static String mapTsv(String keyHeader, String valueHeader, Map<?, ?> map) {
        StringBuilder out = new StringBuilder(keyHeader).append('\t').append(valueHeader).append('\n');
        map.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> out.append(cell(entry.getKey().toString())).append('\t')
                        .append(cell(entry.getValue().toString())).append('\n'));
        return out.toString();
    }

    static Map<String, Long> counters() {
        return new LinkedHashMap<>();
    }

    static Map<String, String> strings() {
        return new LinkedHashMap<>();
    }

    static List<EvidenceRecord> records() {
        return new ArrayList<>();
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":\"" + json(value) + "\"";
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String cell(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ');
    }
}
