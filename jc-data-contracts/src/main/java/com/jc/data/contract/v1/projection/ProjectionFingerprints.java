package com.jc.data.contract.v1.projection;

import com.jc.data.contract.support.Sha256DigestV1;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ProjectionFingerprints {
    private ProjectionFingerprints() {
    }

    public static String sourceSetFingerprint(List<CheckpointSource> sources) {
        List<Map<String, Object>> canonicalSources = sources.stream()
                .sorted(Comparator.comparing(CheckpointSource::sourceEventRef)
                        .thenComparing(CheckpointSource::sourceFingerprint)
                        .thenComparing(source -> source.adapterEvidenceRef() == null ? "" : source.adapterEvidenceRef()))
                .map(source -> {
                    LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
                    fields.put("sourceEventRef", source.sourceEventRef());
                    fields.put("sourceFingerprint", source.sourceFingerprint());
                    fields.put("adapterEvidenceRef", source.adapterEvidenceRef());
                    fields.put("occurredAt", source.occurredAt());
                    fields.put("ingestedAt", source.ingestedAt());
                    return java.util.Collections.unmodifiableMap(fields);
                })
                .toList();
        return fingerprint("data-source-set-sha256-v1", Map.of("sources", canonicalSources));
    }

    public static String checkpointDefinitionFingerprint(
            String sourceStream,
            String sourceContractVersion,
            String sourceSchemaVersion,
            Instant eventTimeFrom,
            Instant eventTimeTo,
            Instant ingestedAtUpperBound,
            long sourceEventCount,
            String sourceSetFingerprint) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceStream", sourceStream);
        fields.put("sourceContractVersion", sourceContractVersion);
        fields.put("sourceSchemaVersion", sourceSchemaVersion);
        fields.put("eventTimeFrom", eventTimeFrom);
        fields.put("eventTimeTo", eventTimeTo);
        fields.put("ingestedAtUpperBound", ingestedAtUpperBound);
        fields.put("sourceEventCount", sourceEventCount);
        fields.put("sourceSetFingerprint", sourceSetFingerprint);
        return fingerprint("data-checkpoint-definition-sha256-v1", fields);
    }

    public static String lineageEntryFingerprint(Map<String, Object> fields) {
        return fingerprint("data-projection-lineage-entry-sha256-v1", fields);
    }

    public static String lineageFingerprint(List<ProjectionLineage> lineage) {
        List<String> fingerprints = lineage.stream()
                .map(ProjectionLineage::lineageEntryFingerprint)
                .sorted()
                .toList();
        return fingerprint("data-projection-lineage-sha256-v1", Map.of("entries", fingerprints));
    }

    public static String recordFingerprint(Map<String, Object> fields) {
        return fingerprint("data-projection-record-sha256-v1", fields);
    }

    public static String snapshotFingerprint(
            ProjectionDefinition definition,
            String checkpointRef,
            Instant snapshotAsOf,
            List<? extends ProjectionRecord> records) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectionName", definition.projectionName());
        fields.put("projectionSchemaVersion", definition.projectionSchemaVersion());
        fields.put("projectionPolicyVersion", definition.projectionPolicyVersion());
        fields.put("featurePolicyVersion", definition.featurePolicyVersion());
        fields.put("identityBindingVersion", definition.identityBindingVersion());
        fields.put("targetContractVersion", definition.targetContractVersion());
        fields.put("sourceCheckpointRef", checkpointRef);
        fields.put("snapshotAsOf", snapshotAsOf);
        fields.put("recordFingerprints", records.stream()
                .map(ProjectionRecord::projectionRecordFingerprint)
                .sorted()
                .toList());
        return fingerprint("data-projection-snapshot-sha256-v1", fields);
    }

    public static String fingerprint(String domain, Map<String, ?> fields) {
        StringBuilder output = new StringBuilder();
        appendString(output, domain);
        output.append(':');
        appendObject(output, fields);
        return Sha256DigestV1.lowercaseHex(output.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Boolean bool) {
            output.append(bool.booleanValue() ? "true" : "false");
        } else if (value instanceof Instant instant) {
            appendString(output, instant.toString());
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            output.append(value);
        } else if (value instanceof BigDecimal decimal) {
            appendDecimal(output, decimal);
        } else if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("non-finite number");
            }
            appendDecimal(output, BigDecimal.valueOf(number.doubleValue()));
        } else if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw new IllegalArgumentException("non-finite number");
            }
            appendDecimal(output, new BigDecimal(Float.toString(number.floatValue())));
        } else if (value instanceof Map<?, ?> map) {
            appendObject(output, stringKeyMap(map));
        } else if (value instanceof List<?> list) {
            appendArray(output, list);
        } else {
            throw new IllegalArgumentException("unsupported canonical type: " + value.getClass().getName());
        }
    }

    private static void appendObject(StringBuilder output, Map<String, ?> values) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                output.append(',');
            }
            first = false;
            appendString(output, entry.getKey());
            output.append(':');
            appendValue(output, entry.getValue());
        }
        output.append('}');
    }

    private static void appendArray(StringBuilder output, List<?> values) {
        output.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendValue(output, values.get(index));
        }
        output.append(']');
    }

    private static void appendDecimal(StringBuilder output, BigDecimal value) {
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        output.append(normalized.toPlainString());
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new IllegalArgumentException("unpaired high surrogate");
                        }
                        output.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("unpaired low surrogate");
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        TreeMap<String, Object> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("canonical map key must be string");
            }
            if (result.put(key, entry.getValue()) != null) {
                throw new IllegalArgumentException("duplicate canonical key");
            }
        }
        return result;
    }

    static List<ProjectionSourceEvent> distinctStableSources(List<ProjectionSourceEvent> sources) {
        TreeMap<String, ProjectionSourceEvent> unique = new TreeMap<>();
        for (ProjectionSourceEvent source : sources) {
            String key = source.sourceEventRef() + '\u001f' + source.sourceFingerprint()
                    + '\u001f' + (source.adapterEvidenceRef() == null ? "" : source.adapterEvidenceRef());
            ProjectionSourceEvent previous = unique.putIfAbsent(key, source);
            if (previous != null && !previous.equals(source)) {
                throw new IllegalArgumentException("conflicting duplicate source event");
            }
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }
}
