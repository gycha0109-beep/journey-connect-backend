package com.jc.intelligence.contract.support;

import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.authority.FeatureAuthorityClass;
import com.jc.intelligence.contract.v1.explanation.ExplanationAudience;
import com.jc.intelligence.contract.v1.explanation.IntelligenceExplanationV1;
import com.jc.intelligence.contract.v1.feature.FeatureValueType;
import com.jc.intelligence.contract.v1.feature.IntelligenceFeatureValueV1;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.IdentitySchemeId;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.inference.InferenceStatus;
import com.jc.intelligence.contract.v1.inference.ModelInferenceRecordV1;
import com.jc.intelligence.contract.v1.inference.ModelType;
import com.jc.intelligence.contract.v1.inference.SafetyResult;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.ExperimentRefV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.run.IntelligenceRunType;
import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceCandidateSnapshotV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceInputSnapshotV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceOutputSnapshotV1;
import com.jc.intelligence.contract.v1.snapshot.OrderingSemantics;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.ModelVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.PromptVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntelligenceContractJsonCodecV1 {
    private IntelligenceContractJsonCodecV1() {
    }

    public static String writeRun(IntelligenceRunV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("runId", value.runId().value());
        map.put("runType", value.runType().wireValue());
        map.put("status", value.status().wireValue());
        put(map, "requestId", value.requestId());
        put(map, "correlationId", value.correlationId());
        put(map, "domainRunMode", value.domainRunMode());
        put(map, "surface", value.surface());
        put(map, "entityRef", value.entityRef() == null ? null : value.entityRef().value());
        put(map, "subjectRef", value.subjectRef() == null ? null : value.subjectRef().value());
        put(map, "identitySchemeId", value.subjectRef() == null
                ? null : value.subjectRef().schemeId().wireValue());
        map.put("inputSnapshotRef", value.inputSnapshotRef().value());
        put(map, "outputSnapshotRef", ref(value.outputSnapshotRef()));
        put(map, "policyVersion", version(value.policyVersion()));
        put(map, "featureDefinitionVersion", version(value.featureDefinitionVersion()));
        put(map, "modelVersion", version(value.modelVersion()));
        put(map, "promptVersion", version(value.promptVersion()));
        map.put("producerBuildId", value.producerBuildId().value());
        map.put("referenceTime", value.referenceTime().toString());
        map.put("startedAt", value.startedAt().toString());
        map.put("completedAt", value.completedAt().toString());
        map.put("replayClass", value.replayClass().wireValue());
        map.put("replayEvidence", replayEvidenceMap(value.replayEvidence()));
        put(map, "fallbackCode", value.fallbackCode());
        put(map, "failureCode", value.failureCode());
        put(map, "experimentRef", experimentMap(value.experimentRef()));
        put(map, "exposureSourceRef", value.exposureSourceRef() == null
                ? null : value.exposureSourceRef().wireValue());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceRunV1 readRun(String json) {
        Map<String, Object> map = object(json);
        SubjectRef subjectRef = null;
        String subjectValue = optionalString(map, "subjectRef");
        if (subjectValue != null) {
            subjectRef = new SubjectRef(
                    IdentitySchemeId.fromWire(requiredString(map, "identitySchemeId")),
                    subjectValue);
        }
        ReplayClass replayClass = ReplayClass.fromWire(requiredString(map, "replayClass"));
        return new IntelligenceRunV1(
                new ContractId(requiredString(map, "contractVersion")),
                new RunRef(requiredString(map, "runId")),
                IntelligenceRunType.fromWire(requiredString(map, "runType")),
                IntelligenceRunStatus.fromWire(requiredString(map, "status")),
                optionalString(map, "requestId"),
                optionalString(map, "correlationId"),
                optionalString(map, "domainRunMode"),
                optionalString(map, "surface"),
                optionalString(map, "entityRef") == null
                        ? null : new EntityRef(optionalString(map, "entityRef")),
                subjectRef,
                new SnapshotRef(requiredString(map, "inputSnapshotRef")),
                optionalString(map, "outputSnapshotRef") == null
                        ? null : new SnapshotRef(optionalString(map, "outputSnapshotRef")),
                optionalString(map, "policyVersion") == null
                        ? null : new PolicyVersion(optionalString(map, "policyVersion")),
                optionalString(map, "featureDefinitionVersion") == null
                        ? null : new FeatureDefinitionVersion(optionalString(map, "featureDefinitionVersion")),
                optionalString(map, "modelVersion") == null
                        ? null : new ModelVersion(optionalString(map, "modelVersion")),
                optionalString(map, "promptVersion") == null
                        ? null : new PromptVersion(optionalString(map, "promptVersion")),
                new ProducerBuildId(requiredString(map, "producerBuildId")),
                Instant.parse(requiredString(map, "referenceTime")),
                Instant.parse(requiredString(map, "startedAt")),
                Instant.parse(requiredString(map, "completedAt")),
                replayClass,
                readReplayEvidence(requiredObject(map, "replayEvidence"), replayClass),
                optionalString(map, "fallbackCode"),
                optionalString(map, "failureCode"),
                readExperiment(optionalObject(map, "experimentRef")),
                optionalString(map, "exposureSourceRef") == null
                        ? null : ExposureSourceId.fromWire(optionalString(map, "exposureSourceRef")));
    }

    public static String writeInputSnapshot(IntelligenceInputSnapshotV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("snapshotId", value.snapshotId().value());
        map.put("schemaVersion", value.schemaVersion().value());
        map.put("sourceRefs", value.sourceRefs());
        put(map, "identityContextRef", value.identityContextRef());
        map.put("referenceTime", value.referenceTime().toString());
        map.put("canonicalizationVersion", value.canonicalizationVersion().value());
        map.put("contentHash", value.contentHash());
        map.put("payloadSizeBytes", value.payloadSizeBytes());
        map.put("privacyClass", value.privacyClass().wireValue());
        map.put("producerBuildId", value.producerBuildId().value());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceInputSnapshotV1 readInputSnapshot(String json) {
        Map<String, Object> map = object(json);
        return new IntelligenceInputSnapshotV1(
                new ContractId(requiredString(map, "contractVersion")),
                new SnapshotRef(requiredString(map, "snapshotId")),
                new SchemaVersion(requiredString(map, "schemaVersion")),
                stringList(map, "sourceRefs"),
                optionalString(map, "identityContextRef"),
                Instant.parse(requiredString(map, "referenceTime")),
                new SchemaVersion(requiredString(map, "canonicalizationVersion")),
                requiredString(map, "contentHash"),
                requiredLong(map, "payloadSizeBytes"),
                PrivacyClass.fromWire(requiredString(map, "privacyClass")),
                new ProducerBuildId(requiredString(map, "producerBuildId")));
    }

    public static String writeCandidateSnapshot(IntelligenceCandidateSnapshotV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("snapshotId", value.snapshotId().value());
        map.put("schemaVersion", value.schemaVersion().value());
        map.put("candidateRefs", value.candidateRefs().stream().map(EntityRef::value).toList());
        map.put("orderingSemantics", value.orderingSemantics().wireValue());
        put(map, "domainExtensionRef", value.domainExtensionRef());
        map.put("contentHash", value.contentHash());
        map.put("producerBuildId", value.producerBuildId().value());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceCandidateSnapshotV1 readCandidateSnapshot(String json) {
        Map<String, Object> map = object(json);
        return new IntelligenceCandidateSnapshotV1(
                new ContractId(requiredString(map, "contractVersion")),
                new SnapshotRef(requiredString(map, "snapshotId")),
                new SchemaVersion(requiredString(map, "schemaVersion")),
                stringList(map, "candidateRefs").stream().map(EntityRef::new).toList(),
                OrderingSemantics.fromWire(requiredString(map, "orderingSemantics")),
                optionalString(map, "domainExtensionRef"),
                requiredString(map, "contentHash"),
                new ProducerBuildId(requiredString(map, "producerBuildId")));
    }

    public static String writeOutputSnapshot(IntelligenceOutputSnapshotV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("snapshotId", value.snapshotId().value());
        map.put("schemaVersion", value.schemaVersion().value());
        map.put("resultRefs", value.resultRefs().stream().map(EntityRef::value).toList());
        map.put("explanationRefs", value.explanationRefs());
        put(map, "constraintResultRef", value.constraintResultRef());
        put(map, "fallbackCode", value.fallbackCode());
        put(map, "domainExtensionRef", value.domainExtensionRef());
        map.put("contentHash", value.contentHash());
        map.put("producerBuildId", value.producerBuildId().value());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceOutputSnapshotV1 readOutputSnapshot(String json) {
        Map<String, Object> map = object(json);
        return new IntelligenceOutputSnapshotV1(
                new ContractId(requiredString(map, "contractVersion")),
                new SnapshotRef(requiredString(map, "snapshotId")),
                new SchemaVersion(requiredString(map, "schemaVersion")),
                stringList(map, "resultRefs").stream().map(EntityRef::new).toList(),
                stringList(map, "explanationRefs"),
                optionalString(map, "constraintResultRef"),
                optionalString(map, "fallbackCode"),
                optionalString(map, "domainExtensionRef"),
                requiredString(map, "contentHash"),
                new ProducerBuildId(requiredString(map, "producerBuildId")));
    }

    public static String writeFeatureValue(IntelligenceFeatureValueV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("featureNamespace", value.featureNamespace());
        map.put("featureName", value.featureName());
        map.put("valueType", value.valueType().wireValue());
        map.put("value", value.value());
        map.put("authorityClass", value.authorityClass().wireValue());
        map.put("sourceRef", value.sourceRef());
        map.put("definitionVersion", value.definitionVersion().value());
        map.put("observedAt", value.observedAt().toString());
        put(map, "validFrom", instant(value.validFrom()));
        put(map, "validUntil", instant(value.validUntil()));
        put(map, "confidence", value.confidence());
        map.put("privacyClass", value.privacyClass().wireValue());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceFeatureValueV1 readFeatureValue(String json) {
        Map<String, Object> map = object(json);
        FeatureValueType type = FeatureValueType.fromWire(requiredString(map, "valueType"));
        return new IntelligenceFeatureValueV1(
                new ContractId(requiredString(map, "contractVersion")),
                requiredString(map, "featureNamespace"),
                requiredString(map, "featureName"),
                type,
                map.get("value"),
                FeatureAuthorityClass.fromWire(requiredString(map, "authorityClass")),
                requiredString(map, "sourceRef"),
                new FeatureDefinitionVersion(requiredString(map, "definitionVersion")),
                Instant.parse(requiredString(map, "observedAt")),
                optionalInstant(map, "validFrom"),
                optionalInstant(map, "validUntil"),
                optionalDouble(map, "confidence"),
                PrivacyClass.fromWire(requiredString(map, "privacyClass")));
    }

    public static String writeExplanation(IntelligenceExplanationV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("explanationId", value.explanationId());
        map.put("runId", value.runId().value());
        map.put("audience", value.audience().wireValue());
        map.put("reasonCodes", value.reasonCodes());
        put(map, "message", value.message());
        map.put("evidenceRefs", value.evidenceRefs());
        map.put("attributes", value.attributes());
        map.put("privacyClass", value.privacyClass().wireValue());
        map.put("createdAt", value.createdAt().toString());
        return ContractJsonWireV1.stringify(map);
    }

    public static IntelligenceExplanationV1 readExplanation(String json) {
        Map<String, Object> map = object(json);
        return new IntelligenceExplanationV1(
                new ContractId(requiredString(map, "contractVersion")),
                requiredString(map, "explanationId"),
                new RunRef(requiredString(map, "runId")),
                ExplanationAudience.fromWire(requiredString(map, "audience")),
                stringList(map, "reasonCodes"),
                optionalString(map, "message"),
                stringList(map, "evidenceRefs"),
                stringMap(map, "attributes"),
                PrivacyClass.fromWire(requiredString(map, "privacyClass")),
                Instant.parse(requiredString(map, "createdAt")));
    }

    public static String writeInference(ModelInferenceRecordV1 value) {
        LinkedHashMap<String, Object> map = base(value.contractVersion().value());
        map.put("inferenceId", value.inferenceId());
        map.put("runId", value.runId().value());
        map.put("status", value.status().wireValue());
        map.put("modelType", value.modelType().wireValue());
        map.put("modelVersion", value.modelVersion().value());
        map.put("promptVersion", value.promptVersion().value());
        put(map, "toolVersion", version(value.toolVersion()));
        map.put("parameterSnapshotRef", value.parameterSnapshotRef().value());
        map.put("inputSnapshotRef", value.inputSnapshotRef().value());
        put(map, "outputSnapshotRef", ref(value.outputSnapshotRef()));
        map.put("safetyPolicyVersion", value.safetyPolicyVersion().value());
        map.put("safetyResult", value.safetyResult().wireValue());
        map.put("producerBuildId", value.producerBuildId().value());
        map.put("startedAt", value.startedAt().toString());
        map.put("completedAt", value.completedAt().toString());
        map.put("latencyMillis", value.latency().toMillis());
        map.put("tokenOrComputeUsage", value.tokenOrComputeUsage());
        put(map, "failureCode", value.failureCode());
        put(map, "fallbackCode", value.fallbackCode());
        put(map, "resultHash", value.resultHash());
        map.put("replayClass", value.replayClass().wireValue());
        map.put("deterministicProviderGuarantee", value.deterministicProviderGuarantee());
        return ContractJsonWireV1.stringify(map);
    }

    public static ModelInferenceRecordV1 readInference(String json) {
        Map<String, Object> map = object(json);
        return new ModelInferenceRecordV1(
                new ContractId(requiredString(map, "contractVersion")),
                requiredString(map, "inferenceId"),
                new RunRef(requiredString(map, "runId")),
                InferenceStatus.fromWire(requiredString(map, "status")),
                ModelType.fromWire(requiredString(map, "modelType")),
                new ModelVersion(requiredString(map, "modelVersion")),
                new PromptVersion(requiredString(map, "promptVersion")),
                optionalString(map, "toolVersion") == null
                        ? null : new SchemaVersion(optionalString(map, "toolVersion")),
                new SnapshotRef(requiredString(map, "parameterSnapshotRef")),
                new SnapshotRef(requiredString(map, "inputSnapshotRef")),
                optionalString(map, "outputSnapshotRef") == null
                        ? null : new SnapshotRef(optionalString(map, "outputSnapshotRef")),
                new SchemaVersion(requiredString(map, "safetyPolicyVersion")),
                SafetyResult.fromWire(requiredString(map, "safetyResult")),
                new ProducerBuildId(requiredString(map, "producerBuildId")),
                Instant.parse(requiredString(map, "startedAt")),
                Instant.parse(requiredString(map, "completedAt")),
                Duration.ofMillis(requiredLong(map, "latencyMillis")),
                longMap(map, "tokenOrComputeUsage"),
                optionalString(map, "failureCode"),
                optionalString(map, "fallbackCode"),
                optionalString(map, "resultHash"),
                ReplayClass.fromWire(requiredString(map, "replayClass")),
                requiredBoolean(map, "deterministicProviderGuarantee"));
    }

    private static LinkedHashMap<String, Object> base(String contractVersion) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("contractVersion", contractVersion);
        return map;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String ref(SnapshotRef value) {
        return value == null ? null : value.value();
    }

    private static String version(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof PolicyVersion v) {
            return v.value();
        }
        if (value instanceof FeatureDefinitionVersion v) {
            return v.value();
        }
        if (value instanceof ModelVersion v) {
            return v.value();
        }
        if (value instanceof PromptVersion v) {
            return v.value();
        }
        if (value instanceof SchemaVersion v) {
            return v.value();
        }
        throw new IllegalArgumentException("Unsupported version type: " + value.getClass().getName());
    }

    private static Map<String, Object> replayEvidenceMap(ReplayEvidenceDescriptorV1 value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("deterministicPath", value.deterministicPath());
        map.put("immutableInputBound", value.immutableInputBound());
        map.put("immutableOutputBound", value.immutableOutputBound());
        map.put("versionsBound", value.versionsBound());
        map.put("deterministicSeedBound", value.deterministicSeedBound());
        map.put("modelOrProviderInferenceUsed", value.modelOrProviderInferenceUsed());
        return map;
    }

    private static ReplayEvidenceDescriptorV1 readReplayEvidence(
            Map<String, Object> map,
            ReplayClass replayClass) {
        return new ReplayEvidenceDescriptorV1(
                replayClass,
                requiredBoolean(map, "deterministicPath"),
                requiredBoolean(map, "immutableInputBound"),
                requiredBoolean(map, "immutableOutputBound"),
                requiredBoolean(map, "versionsBound"),
                requiredBoolean(map, "deterministicSeedBound"),
                requiredBoolean(map, "modelOrProviderInferenceUsed"));
    }

    private static Map<String, Object> experimentMap(ExperimentRefV1 value) {
        if (value == null) {
            return null;
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("experimentId", value.experimentId());
        map.put("experimentVersion", value.experimentVersion().value());
        put(map, "assignmentId", value.assignmentId());
        return map;
    }

    private static ExperimentRefV1 readExperiment(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new ExperimentRefV1(
                requiredString(map, "experimentId"),
                new SchemaVersion(requiredString(map, "experimentVersion")),
                optionalString(map, "assignmentId"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json) {
        Object parsed = StrictContractJsonParserV1.parse(json);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredObject(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalObject(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    private static String requiredString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return string;
    }

    private static String optionalString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return string;
    }

    private static boolean requiredBoolean(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return bool.booleanValue();
    }

    private static long requiredLong(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return number.longValue();
    }

    private static Double optionalDouble(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return number.doubleValue();
    }

    private static Instant optionalInstant(Map<String, Object> map, String field) {
        String value = optionalString(map, field);
        return value == null ? null : Instant.parse(value);
    }

    private static List<String> stringList(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof List<?> source)) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        ArrayList<String> result = new ArrayList<>();
        for (Object item : source) {
            if (!(item instanceof String string)) {
                throw new IllegalArgumentException(field + " items must be strings");
            }
            result.add(string);
        }
        return result;
    }

    private static Map<String, String> stringMap(Map<String, Object> map, String field) {
        Map<String, Object> source = requiredObject(map, field);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(value instanceof String string)) {
                throw new IllegalArgumentException(field + " values must be strings");
            }
            result.put(key, string);
        });
        return result;
    }

    private static Map<String, Long> longMap(Map<String, Object> map, String field) {
        Map<String, Object> source = requiredObject(map, field);
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(field + " values must be numbers");
            }
            result.put(key, number.longValue());
        });
        return result;
    }
}
