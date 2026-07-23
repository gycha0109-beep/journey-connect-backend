package com.jc.data.contract.v1.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CrossTrackFingerprints {
    public static final String INPUT_DOMAIN = "integration-input-sha256-v1";
    public static final String CHECK_DOMAIN = "integration-check-evidence-sha256-v1";
    public static final String MAPPING_DOMAIN = "integration-mapping-sha256-v1";
    public static final String VERDICT_DOMAIN = "integration-verdict-sha256-v1";
    public static final String MATRIX_DOMAIN = "cross-track-contract-matrix-sha256-v1";
    private CrossTrackFingerprints() { }

    public static String logicalIdentity(CrossTrackIntegrationContext context) {
        return fingerprint(INPUT_DOMAIN, Map.of(
                "sourceSnapshotRef", context.sourceSnapshot().snapshotRef(),
                "sourceTrack", context.definition().sourceTrack(),
                "targetTrack", context.definition().targetTrack(),
                "sourceContract", context.definition().sourceContract(),
                "targetContract", context.definition().targetContract(),
                "integrationScope", context.definition().integrationScope().name(),
                "validatorVersion", context.definition().validatorVersion(),
                "integrationPolicyVersion", context.definition().integrationPolicyVersion()));
    }
    public static String input(CrossTrackIntegrationContext context) {
        return fingerprint(INPUT_DOMAIN, Map.ofEntries(
                Map.entry("sourceTrack", context.definition().sourceTrack()),
                Map.entry("targetTrack", context.definition().targetTrack()),
                Map.entry("sourceContract", context.definition().sourceContract()),
                Map.entry("sourceSchemaVersion", context.definition().sourceSchemaVersion()),
                Map.entry("targetContract", context.definition().targetContract()),
                Map.entry("targetSchemaVersion", context.definition().targetSchemaVersion()),
                Map.entry("sourceSnapshotFingerprint", context.sourceSnapshot().contentFingerprint()),
                Map.entry("sourceLineageFingerprint", context.sourceSnapshot().lineageFingerprint()),
                Map.entry("sourceQualityVerdictFingerprint", context.qualityVerdict() == null ? "" : context.qualityVerdict().verdictFingerprint()),
                Map.entry("mappingPolicyVersion", context.definition().mappingPolicyVersion()),
                Map.entry("integrationPolicyVersion", context.definition().integrationPolicyVersion()),
                Map.entry("identityBindingFingerprint", context.identityBinding() == null ? "" : context.identityBinding().bindingFingerprint()),
                Map.entry("mappingFingerprint", mapping(context.contractMapping()))));
    }
    public static String check(int order, String code, CrossTrackIntegrationScope scope, String source, String target,
            String expected, String observed, CrossTrackIntegrationSeverity severity,
            CrossTrackIntegrationCheckStatus status, CrossTrackIntegrationFailure failure,
            boolean required, boolean conditional) {
        return fingerprint(CHECK_DOMAIN, Map.ofEntries(
                Map.entry("order", order), Map.entry("checkCode", code), Map.entry("scope", scope.name()),
                Map.entry("sourceReference", source == null ? "" : source),
                Map.entry("targetReference", target == null ? "" : target),
                Map.entry("expected", expected == null ? "" : expected),
                Map.entry("observed", observed == null ? "" : observed),
                Map.entry("severity", severity.name()), Map.entry("status", status.name()),
                Map.entry("failure", failure == null ? "" : failure.wireValue()),
                Map.entry("required", required), Map.entry("conditional", conditional)));
    }
    public static String mapping(CrossTrackContractMapping mapping) {
        return fingerprint(MAPPING_DOMAIN, Map.ofEntries(
                Map.entry("sourceContract", mapping.sourceContract()),
                Map.entry("sourceSchemaVersion", mapping.sourceSchemaVersion()),
                Map.entry("targetContract", mapping.targetContract()),
                Map.entry("targetSchemaVersion", mapping.targetSchemaVersion()),
                Map.entry("mappingPolicyVersion", mapping.mappingPolicyVersion()),
                Map.entry("targetContractPresent", mapping.targetContractPresent()),
                Map.entry("targetAuthorityConfirmed", mapping.targetAuthorityConfirmed()),
                Map.entry("schemaSupported", mapping.schemaSupported()),
                Map.entry("requiredFieldsPresent", mapping.requiredFieldsPresent()),
                Map.entry("semanticsCompatible", mapping.semanticsCompatible()),
                Map.entry("unitsCompatible", mapping.unitsCompatible()),
                Map.entry("domainMappingApproved", mapping.domainMappingApproved()),
                Map.entry("missingRequiredFields", mapping.missingRequiredFields().stream().sorted().toList()),
                Map.entry("semanticMappings", mapping.semanticMappings()),
                Map.entry("unitMappings", mapping.unitMappings())));
    }
    public static String verdict(CrossTrackIntegrationVerdictStatus status, List<CrossTrackIntegrationCheck> checks) {
        List<CrossTrackIntegrationCheck> ordered = checks.stream().sorted(Comparator.comparingInt(CrossTrackIntegrationCheck::order)
                .thenComparing(CrossTrackIntegrationCheck::checkCode)).toList();
        long blockers = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL
                && c.severity() == CrossTrackIntegrationSeverity.BLOCKER).count();
        long errors = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL
                && c.severity() == CrossTrackIntegrationSeverity.ERROR).count();
        long warnings = ordered.stream().filter(c -> c.severity() == CrossTrackIntegrationSeverity.WARNING).count();
        long passed = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.PASS).count();
        long failed = ordered.stream().filter(c -> c.checkStatus() == CrossTrackIntegrationCheckStatus.FAIL).count();
        long skipped = ordered.stream().filter(c -> c.required()
                && c.checkStatus() == CrossTrackIntegrationCheckStatus.SKIPPED).count();
        long conditional = ordered.stream().filter(CrossTrackIntegrationCheck::conditionalRequirement).count();
        return fingerprint(VERDICT_DOMAIN, Map.ofEntries(
                Map.entry("status", status.name()), Map.entry("blockerCount", blockers), Map.entry("errorCount", errors),
                Map.entry("warningCount", warnings), Map.entry("passedCheckCount", passed), Map.entry("failedCheckCount", failed),
                Map.entry("skippedRequiredCheckCount", skipped), Map.entry("conditionalRequirementCount", conditional),
                Map.entry("checkFingerprints", ordered.stream().map(CrossTrackIntegrationCheck::evidenceFingerprint).toList())));
    }
    public static String contractMatrix(List<CrossTrackContractMapping> mappings) {
        List<String> values = mappings.stream().map(CrossTrackFingerprints::mapping).sorted().toList();
        return fingerprint(MATRIX_DOMAIN, Map.of("mappingFingerprints", values));
    }
    public static String fingerprint(String domain, Map<String, ?> fields) {
        StringBuilder output = new StringBuilder();
        appendString(output, domain); output.append(':'); appendObject(output, fields);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(output.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private static void appendValue(StringBuilder output, Object value) {
        if (value == null) output.append("null");
        else if (value instanceof String string) appendString(output, string);
        else if (value instanceof Boolean bool) output.append(bool.booleanValue() ? "true" : "false");
        else if (value instanceof Number number) output.append(new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
        else if (value instanceof Map<?, ?> map) appendObject(output, stringKeyMap(map));
        else if (value instanceof List<?> list) appendArray(output, list);
        else throw new IllegalArgumentException("unsupported canonical type: " + value.getClass().getName());
    }
    private static void appendObject(StringBuilder output, Map<String, ?> map) {
        output.append('{'); boolean first = true;
        for (Map.Entry<String, ?> entry : new TreeMap<>(map).entrySet()) {
            if (!first) output.append(','); first = false; appendString(output, entry.getKey()); output.append(':'); appendValue(output, entry.getValue());
        }
        output.append('}');
    }
    private static void appendArray(StringBuilder output, List<?> list) {
        output.append('['); for (int i=0;i<list.size();i++) { if (i>0) output.append(','); appendValue(output, list.get(i)); } output.append(']');
    }
    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (c < 0x20) {
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        output.append('"');
    }
    private static Map<String,Object> stringKeyMap(Map<?,?> source) {
        TreeMap<String,Object> result=new TreeMap<>();
        for (Map.Entry<?,?> entry:source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("canonical map key must be string");
            result.put(key, entry.getValue());
        }
        return result;
    }
}
