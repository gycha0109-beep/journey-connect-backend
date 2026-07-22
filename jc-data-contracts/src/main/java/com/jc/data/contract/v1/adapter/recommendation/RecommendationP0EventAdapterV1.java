package com.jc.data.contract.v1.adapter.recommendation;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.canonical.CanonicalJsonNormalizerV1;
import com.jc.data.contract.v1.canonical.CanonicalizationRequestV1;
import com.jc.data.contract.v1.event.EventDefinitionV1;
import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.validation.DataContractValidatorsV1;
import com.jc.data.contract.v1.version.Versions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class RecommendationP0EventAdapterV1 implements RecommendationP0EventAdapter {
    private static final Set<String> SURFACES = Set.of("home", "search", "detail", "profile", "crew");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "accesstoken", "refreshtoken", "authorization", "password", "secret", "secretkey",
            "apikey", "token", "rawuserid", "userid", "accountid", "email", "phone",
            "rawquery", "query", "freetext", "rawtext", "rawcontent", "credential");
    private static final Set<String> COMMON_METADATA = Set.of("surface", "position", "dwellTimeMs", "viewportRatio");

    private final RecommendationP0AdapterPolicyV1 policy;
    private final CanonicalJsonNormalizerV1 canonicalizer = new CanonicalJsonNormalizerV1();

    public RecommendationP0EventAdapterV1() {
        this(RecommendationP0AdapterPolicyV1.defaultPolicy());
    }

    public RecommendationP0EventAdapterV1(RecommendationP0AdapterPolicyV1 policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public RecommendationP0AdapterOutputV1 adapt(RecommendationP0AdapterInputV1 input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(input.producerBuildId(), "producerBuildId");
        RecommendationP0BehaviorEventSourceV1 source = Objects.requireNonNull(input.source(), "source");
        String sourceEventRef = source.eventId() == null ? null : "recommendation_behavior_event:" + source.eventId();

        RecommendationP0MappingFailure sourceFailure = validateSource(source);
        if (sourceFailure != null) {
            return failed(sourceEventRef, source.payloadFingerprint(), sourceFailure, input.producerBuildId());
        }
        if (!policy.sourceSchemaVersion().equals(source.schemaVersion())) {
            return failed(sourceEventRef, source.payloadFingerprint(),
                    RecommendationP0MappingFailure.UNSUPPORTED_SCHEMA_VERSION, input.producerBuildId());
        }

        EventType targetType = EventTaxonomyRegistryV1.p0WireMappingCandidates().get(source.eventTypeWire());
        if (targetType == null) {
            return failed(sourceEventRef, source.payloadFingerprint(),
                    RecommendationP0MappingFailure.UNSUPPORTED_EVENT_TYPE, input.producerBuildId());
        }

        RecommendationP0IdentityBindingV1 identity = input.identityBinding();
        if (source.userId() == null || identity == null || identity.sourceUserId() != source.userId().longValue()) {
            return failed(sourceEventRef, source.payloadFingerprint(),
                    RecommendationP0MappingFailure.IDENTITY_MAPPING_REQUIRED, input.producerBuildId());
        }

        try {
            Mapping mapping = map(source, targetType, identity.actorRef(), input.exposureBinding());
            EventDefinitionV1 definition = EventTaxonomyRegistryV1
                    .definition(EventFamily.USER_BEHAVIOR, targetType).orElseThrow();
            if (definition.entityRequired() && mapping.entityRef() == null) {
                return failed(sourceEventRef, source.payloadFingerprint(),
                        RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE, input.producerBuildId());
            }
            if (!DataContractValidatorsV1.validatePayload(mapping.payload(), definition).isEmpty()) {
                return failed(sourceEventRef, source.payloadFingerprint(),
                        RecommendationP0MappingFailure.TARGET_CONTRACT_VIOLATION, input.producerBuildId());
            }
            References.SessionRef sessionRef = privacySafeSessionRef(source.sessionId());
            RecommendationP0MappedEventV1 mapped = new RecommendationP0MappedEventV1(
                    EventFamily.USER_BEHAVIOR,
                    targetType,
                    source.occurredAt(),
                    identity.actorRef(),
                    sessionRef,
                    mapping.entityRef(),
                    mapping.payload(),
                    source.runId() == null ? null : "recommendation_run:" + source.runId(),
                    mapping.authorityEvidenceRef());
            String fingerprint = outputFingerprint(source, mapped);
            return new RecommendationP0AdapterOutputV1(
                    policy.adapterId(), policy.adapterVersion(), policy.mappingPolicyVersion(),
                    policy.outputFingerprintVersion(), sourceEventRef, source.payloadFingerprint(),
                    policy.targetContractVersion(), policy.targetSchemaVersion(),
                    RecommendationP0CompatibilityClass.SEMANTIC_COMPATIBLE,
                    RecommendationP0MappingStatus.MAPPED_SHADOW,
                    mapped, fingerprint, null, input.producerBuildId(), mapping.droppedMetadataKeys());
        } catch (MappingException exception) {
            return failed(sourceEventRef, source.payloadFingerprint(), exception.failure(), input.producerBuildId());
        } catch (IllegalArgumentException exception) {
            return failed(sourceEventRef, source.payloadFingerprint(),
                    RecommendationP0MappingFailure.ADAPTER_INVARIANT_FAILED, input.producerBuildId());
        }
    }

    private RecommendationP0MappingFailure validateSource(RecommendationP0BehaviorEventSourceV1 source) {
        if (source.eventId() == null || !source.eventId().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || source.idempotencyKey() == null || source.idempotencyKey().isBlank()
                || source.idempotencyKey().length() > 160
                || source.sessionId() == null || source.sessionId().isBlank() || source.sessionId().length() > 128
                || source.eventTypeWire() == null || source.eventTypeWire().isBlank()) {
            return RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE;
        }
        if (source.canonicalPayload() == null || source.canonicalPayload().length == 0
                || source.canonicalPayload().length > 262_144
                || source.payloadFingerprint() == null
                || !source.payloadFingerprint().matches("[0-9a-f]{64}")) {
            return RecommendationP0MappingFailure.SOURCE_FINGERPRINT_MISMATCH;
        }
        if (!Sha256DigestV1.lowercaseHex(source.canonicalPayload()).equals(source.payloadFingerprint())) {
            return RecommendationP0MappingFailure.SOURCE_FINGERPRINT_MISMATCH;
        }
        if (source.occurredAt() == null || source.receivedAt() == null
                || source.receivedAt().isBefore(source.occurredAt())) {
            return RecommendationP0MappingFailure.TIMESTAMP_INVALID;
        }
        if (source.userId() != null && source.userId().longValue() <= 0L) {
            return RecommendationP0MappingFailure.IDENTITY_MAPPING_REQUIRED;
        }
        if (source.metadata().size() > 32) {
            return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
        }
        for (Map.Entry<String, Object> entry : source.metadata().entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
                return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
            }
            String normalized = normalizeKey(key);
            if (isSensitive(normalized)) {
                return RecommendationP0MappingFailure.PRIVACY_POLICY_VIOLATION;
            }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Boolean || value instanceof Number)) {
                return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
            }
            if (value instanceof String text && text.length() > 256) {
                return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
            }
            if (value instanceof Double number && !Double.isFinite(number)) {
                return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
            }
            if (value instanceof Float number && !Float.isFinite(number)) {
                return RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE;
            }
        }
        return validateEntityPartition(source);
    }

    private RecommendationP0MappingFailure validateEntityPartition(RecommendationP0BehaviorEventSourceV1 source) {
        boolean none = source.entityType() == null && source.entityKey() == null && source.sourceEntityId() == null;
        boolean full = source.entityType() != null && source.entityKey() != null && source.sourceEntityId() != null;
        if (!none && !full) {
            return RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE;
        }
        if ("search".equals(source.eventTypeWire())) {
            return none ? null : RecommendationP0MappingFailure.TARGET_CONTRACT_VIOLATION;
        }
        if (!full || source.sourceEntityId().longValue() <= 0L
                || !source.entityKey().equals(source.entityType() + ":" + source.sourceEntityId())) {
            return RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE;
        }
        if (!Set.of("post", "journey", "place", "crew", "user").contains(source.entityType())) {
            return RecommendationP0MappingFailure.TARGET_CONTRACT_VIOLATION;
        }
        return null;
    }

    private Mapping map(
            RecommendationP0BehaviorEventSourceV1 source,
            EventType targetType,
            References.ActorRef actor,
            RecommendationP0ExposureBindingV1 exposure) {
        References.EntityRef sourceEntity = source.entityKey() == null ? null : entity(source.entityKey());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        TreeSet<String> consumed = new TreeSet<>();
        String authority = null;
        switch (targetType) {
            case RECOMMENDATION_IMPRESSION, RECOMMENDATION_CLICK -> {
                RecommendationP0ExposureBindingV1 binding = requireExposure(source, exposure);
                String surface = binding.surface();
                requireSurface(surface);
                payload.put("runRef", "recommendation_run:" + binding.runId());
                payload.put("absoluteRank", binding.absoluteRank());
                payload.put("surface", surface);
                if (targetType == EventType.RECOMMENDATION_IMPRESSION) {
                    payload.put("episodeRef", binding.exposureEventRef());
                }
                authority = binding.exposureEventRef();
                consumed.add("surface");
                consumed.add("position");
            }
            case POST_VIEW -> {
                requireEntityType(sourceEntity, "post");
                payload.put("surface", requireMetadataString(source, "surface"));
                consumed.add("surface");
            }
            case POST_LIKE, POST_UNLIKE, POST_BOOKMARK, POST_UNBOOKMARK, FOLLOW, UNFOLLOW,
                    CREW_JOIN, CREW_LEAVE -> {
                String expectedType = switch (targetType) {
                    case FOLLOW, UNFOLLOW -> "user";
                    case CREW_JOIN, CREW_LEAVE -> "crew";
                    default -> "post";
                };
                requireEntityType(sourceEntity, expectedType);
                payload.put("stateTransitionRef", "recommendation_behavior_event:" + source.eventId());
            }
            case POST_SHARE -> {
                requireEntityType(sourceEntity, "post");
                payload.put("shareChannelClass", requireMetadataSnake(source, "shareChannelClass"));
                consumed.add("shareChannelClass");
            }
            case POST_HIDE -> {
                requireEntityType(sourceEntity, "post");
                payload.put("reasonCode", requireMetadataSnake(source, "reasonCode"));
                consumed.add("reasonCode");
            }
            case POST_REPORT -> {
                requireEntityType(sourceEntity, "post");
                payload.put("reportReasonCode", requireMetadataSnake(source, "reportReasonCode"));
                consumed.add("reportReasonCode");
                String reportRef = optionalMetadataString(source, "reportRef");
                if (reportRef != null) {
                    payload.put("reportRef", reportRef);
                    consumed.add("reportRef");
                }
            }
            case SEARCH_SUBMIT -> {
                payload.put("searchRunRef", requireMetadataString(source, "searchRunRef"));
                payload.put("queryRef", requireMetadataString(source, "queryRef"));
                consumed.add("searchRunRef");
                consumed.add("queryRef");
                String surface = optionalMetadataString(source, "surface");
                if (surface != null) {
                    requireSurface(surface);
                    payload.put("surface", surface);
                    consumed.add("surface");
                }
            }
            case TAG_CLICK -> {
                References.EntityRef tagRef = entity(requireMetadataString(source, "tagRef"));
                requireEntityType(tagRef, "tag");
                payload.put("tagRef", tagRef.value());
                payload.put("surface", requireMetadataString(source, "surface"));
                requireSurface((String) payload.get("surface"));
                consumed.add("tagRef");
                consumed.add("surface");
                sourceEntity = tagRef;
            }
            default -> throw new MappingException(RecommendationP0MappingFailure.UNSUPPORTED_EVENT_TYPE);
        }
        List<String> dropped = new ArrayList<>();
        for (String key : source.metadata().keySet()) {
            if (!consumed.contains(key) && !COMMON_METADATA.contains(key)) {
                dropped.add(key);
            }
        }
        dropped.sort(String::compareTo);
        return new Mapping(sourceEntity, Map.copyOf(payload), authority, List.copyOf(dropped));
    }

    private RecommendationP0ExposureBindingV1 requireExposure(
            RecommendationP0BehaviorEventSourceV1 source,
            RecommendationP0ExposureBindingV1 binding) {
        if (binding == null) {
            throw new MappingException(RecommendationP0MappingFailure.MISSING_EXPOSURE_REFERENCE);
        }
        if (RecommendationP0AdapterPolicyV1.P2_EXPERIMENT_EXPOSURE_AUTHORITY.equals(binding.authorityId())
                || !RecommendationP0AdapterPolicyV1.GENERAL_EXPOSURE_AUTHORITY.equals(binding.authorityId())) {
            throw new MappingException(RecommendationP0MappingFailure.EXPOSURE_AUTHORITY_CONFLICT);
        }
        if (source.runId() == null || !source.runId().equals(binding.runId())
                || source.entityKey() == null || !source.entityKey().equals(binding.entityKey())) {
            throw new MappingException(RecommendationP0MappingFailure.MISSING_EXPOSURE_REFERENCE);
        }
        Object surface = source.metadata().get("surface");
        if (surface != null && !binding.surface().equals(surface)) {
            throw new MappingException(RecommendationP0MappingFailure.EXPOSURE_AUTHORITY_CONFLICT);
        }
        Object position = source.metadata().get("position");
        if (position instanceof Number number && number.intValue() != binding.pagePosition()) {
            throw new MappingException(RecommendationP0MappingFailure.EXPOSURE_AUTHORITY_CONFLICT);
        }
        return binding;
    }

    private String outputFingerprint(
            RecommendationP0BehaviorEventSourceV1 source,
            RecommendationP0MappedEventV1 mapped) {
        LinkedHashMap<String, Object> approved = new LinkedHashMap<>();
        approved.put("adapterId", policy.adapterId());
        approved.put("adapterVersion", policy.adapterVersion());
        approved.put("mappingPolicyVersion", policy.mappingPolicyVersion());
        approved.put("outputFingerprintVersion", policy.outputFingerprintVersion());
        approved.put("sourceEventRef", "recommendation_behavior_event:" + source.eventId());
        approved.put("sourceFingerprint", source.payloadFingerprint());
        approved.put("sourceSchemaVersion", source.schemaVersion());
        approved.put("targetContractVersion", policy.targetContractVersion());
        approved.put("targetSchemaVersion", policy.targetSchemaVersion());
        approved.put("targetCanonicalizationVersion", policy.targetCanonicalizationVersion());
        approved.put("eventFamily", mapped.eventFamily().wireValue());
        approved.put("eventType", mapped.eventType().wireValue());
        approved.put("occurredAt", mapped.occurredAt());
        approved.put("mappedActor", mapped.actorRef().value());
        approved.put("mappedSession", mapped.sessionRef().value());
        approved.put("mappedEntity", mapped.entityRef() == null ? null : mapped.entityRef().value());
        approved.put("sourceRunRef", mapped.sourceRunRef());
        approved.put("authorityEvidenceRef", mapped.authorityEvidenceRef());
        approved.put("payload", mapped.payload());
        var result = canonicalizer.canonicalize(new CanonicalizationRequestV1(
                new Versions.CanonicalizationVersion(policy.targetCanonicalizationVersion()), approved));
        if (!result.isSuccess()) {
            throw new MappingException(RecommendationP0MappingFailure.ADAPTER_INVARIANT_FAILED);
        }
        return Sha256DigestV1.lowercaseHex(result.canonicalBytes());
    }

    private static References.SessionRef privacySafeSessionRef(String sourceSessionId) {
        String digest = Sha256DigestV1.lowercaseHex(sourceSessionId.getBytes(StandardCharsets.UTF_8));
        return new References.SessionRef("session:p0-" + digest);
    }

    private RecommendationP0AdapterOutputV1 failed(
            String sourceEventRef,
            String sourceFingerprint,
            RecommendationP0MappingFailure failure,
            Versions.ProducerBuildId buildId) {
        RecommendationP0MappingStatus status = switch (failure) {
            case SOURCE_FINGERPRINT_MISMATCH, PRIVACY_POLICY_VIOLATION,
                    TARGET_CONTRACT_VIOLATION, ADAPTER_INVARIANT_FAILED,
                    EXPOSURE_AUTHORITY_CONFLICT, UNCLASSIFIED_MAPPING_FAILURE ->
                    RecommendationP0MappingStatus.QUARANTINED;
            default -> RecommendationP0MappingStatus.UNSUPPORTED;
        };
        return new RecommendationP0AdapterOutputV1(
                policy.adapterId(), policy.adapterVersion(), policy.mappingPolicyVersion(),
                policy.outputFingerprintVersion(), sourceEventRef, sourceFingerprint,
                policy.targetContractVersion(), policy.targetSchemaVersion(),
                RecommendationP0CompatibilityClass.UNSUPPORTED, status,
                null, null, failure, buildId, List.of());
    }

    private static References.EntityRef entity(String value) {
        try {
            return new References.EntityRef(value);
        } catch (IllegalArgumentException exception) {
            throw new MappingException(RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE);
        }
    }

    private static void requireEntityType(References.EntityRef entity, String expected) {
        if (entity == null || !expected.equals(entity.entityType())) {
            throw new MappingException(RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE);
        }
    }

    private static String requireMetadataString(RecommendationP0BehaviorEventSourceV1 source, String key) {
        String value = optionalMetadataString(source, key);
        if (value == null || value.isBlank()) {
            throw new MappingException(RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE);
        }
        return value;
    }

    private static String requireMetadataSnake(RecommendationP0BehaviorEventSourceV1 source, String key) {
        String value = requireMetadataString(source, key);
        if (!DataContractValidatorsV1.isLowerSnakeCase(value)) {
            throw new MappingException(RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE);
        }
        return value;
    }

    private static String optionalMetadataString(RecommendationP0BehaviorEventSourceV1 source, String key) {
        Object value = source.metadata().get(key);
        return value instanceof String text ? text : null;
    }

    private static void requireSurface(String surface) {
        if (!SURFACES.contains(surface)) {
            throw new MappingException(RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE);
        }
    }

    private static String normalizeKey(String key) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char character = Character.toLowerCase(key.charAt(index));
            if (Character.isLetterOrDigit(character)) {
                output.append(character);
            }
        }
        return output.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isSensitive(String normalized) {
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("token")
                || normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("credential");
    }

    private record Mapping(
            References.EntityRef entityRef,
            Map<String, Object> payload,
            String authorityEvidenceRef,
            List<String> droppedMetadataKeys) {
    }

    private static final class MappingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final RecommendationP0MappingFailure failure;

        private MappingException(RecommendationP0MappingFailure failure) {
            super(failure.wireValue());
            this.failure = failure;
        }

        private RecommendationP0MappingFailure failure() {
            return failure;
        }
    }
}
