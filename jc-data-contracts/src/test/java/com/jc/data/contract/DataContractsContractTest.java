package com.jc.data.contract;

import com.jc.data.contract.support.StrictContractJsonParserV1;
import com.jc.data.contract.v1.canonical.CanonicalJsonNormalizerV1;
import com.jc.data.contract.v1.canonical.CanonicalizationRequestV1;
import com.jc.data.contract.v1.command.ClientEventCommandV1;
import com.jc.data.contract.v1.compatibility.CompatibilityClassification;
import com.jc.data.contract.v1.compatibility.CompatibilityEvaluatorV1;
import com.jc.data.contract.v1.compatibility.CompatibilityRequestV1;
import com.jc.data.contract.v1.compatibility.DataContractRegistryV1;
import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.event.PlatformEventEnvelopeV1;
import com.jc.data.contract.v1.fingerprint.FingerprintRequestV1;
import com.jc.data.contract.v1.fingerprint.FingerprintStatus;
import com.jc.data.contract.v1.fingerprint.UnresolvedEventFingerprintBoundaryV1;
import com.jc.data.contract.v1.idempotency.IdempotencyClassification;
import com.jc.data.contract.v1.idempotency.IdempotencyComparisonV1;
import com.jc.data.contract.v1.identity.IdentityScheme;
import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.validation.ClientEventCommandValidatorV1;
import com.jc.data.contract.v1.validation.ContractIdValidatorV1;
import com.jc.data.contract.v1.validation.DataValidationErrorCode;
import com.jc.data.contract.v1.validation.EventWireValidatorV1;
import com.jc.data.contract.v1.validation.PlatformEventEnvelopeValidatorV1;
import com.jc.data.contract.v1.validation.ReferenceValidatorV1;
import com.jc.data.contract.v1.validation.VersionValidatorV1;
import com.jc.data.contract.v1.version.Versions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class DataContractsContractTest {
    private static int assertions;

    private DataContractsContractTest() {
    }

    public static void main(String[] args) throws Exception {
        versionContracts();
        rawValidationContracts();
        contractRegistryContracts();
        identityContracts();
        taxonomyContracts();
        commandValidationContracts();
        envelopeValidationContracts();
        canonicalizationContracts();
        fingerprintBoundaryContracts();
        idempotencyContracts();
        compatibilityContracts();
        protectedFixtureContracts();
        architectureIsolation();
        fixtureSafety();
        System.out.println("DP-1 data contract checks passed: " + assertions);
    }

    private static void versionContracts() {
        check(new Versions.ContractVersion("platform-event-v1").value().equals("platform-event-v1"),
                "contract version accepted");
        check(new Versions.SchemaVersion("user-behavior-event-v1").value().equals("user-behavior-event-v1"),
                "schema version accepted");
        check(new Versions.ProducerBuildId("git:0123456789abcdef0123456789abcdef01234567")
                .value().startsWith("git:"), "build ID accepted");
        expectFailure(() -> new Versions.ContractVersion("latest"));
        expectFailure(() -> new Versions.SchemaVersion("current"));
        expectFailure(() -> new Versions.ProducerVersion("default"));
        expectFailure(() -> new Versions.ConsumerVersion("data-event-consumer"));
        expectFailure(() -> new Versions.ProducerBuildId("git:ABCDEF"));
    }


    private static void rawValidationContracts() {
        check(VersionValidatorV1.contractVersion("platform-event-v1").isValid(),
                "raw contract version accepted");
        check(hasCode(VersionValidatorV1.contractVersion("latest").errors(),
                DataValidationErrorCode.INVALID_CONTRACT_VERSION),
                "raw invalid contract version has stable code");
        check(hasCode(VersionValidatorV1.schemaVersion("current").errors(),
                DataValidationErrorCode.INVALID_SCHEMA_VERSION),
                "raw invalid schema version has stable code");
        check(hasCode(VersionValidatorV1.canonicalizationVersion("default").errors(),
                DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION),
                "raw invalid canonicalization version has stable code");
        check(hasCode(VersionValidatorV1.producerVersion("producer").errors(),
                DataValidationErrorCode.INVALID_PRODUCER_VERSION),
                "raw invalid producer version has stable code");
        check(hasCode(VersionValidatorV1.consumerVersion("consumer").errors(),
                DataValidationErrorCode.INVALID_CONSUMER_VERSION),
                "raw invalid consumer version has stable code");
        check(hasCode(VersionValidatorV1.producerBuildId("git:ABC").errors(),
                DataValidationErrorCode.INVALID_PRODUCER_BUILD_ID),
                "raw invalid build ID has stable code");
        check(ReferenceValidatorV1.eventId("event:abc").isValid(), "raw event ID accepted");
        check(hasCode(ReferenceValidatorV1.eventId("abc").errors(),
                DataValidationErrorCode.INVALID_EVENT_ID), "malformed event ID stable code");
        check(hasCode(ReferenceValidatorV1.canonicalActorRef("user:10").errors(),
                DataValidationErrorCode.IDENTITY_NAMESPACE_MISMATCH),
                "legacy actor namespace rejected without conversion");
        check(hasCode(ReferenceValidatorV1.subjectRef(IdentityScheme.PLATFORM_SUBJECT_V1, "10").errors(),
                DataValidationErrorCode.RAW_NUMERIC_IDENTITY_FORBIDDEN),
                "raw numeric identity rejected");
        check(EventWireValidatorV1.family("user_behavior").isValid(), "raw event family accepted");
        check(EventWireValidatorV1.type("post_view").isValid(), "raw event type accepted");
        check(hasCode(EventWireValidatorV1.type("PostView").errors(),
                DataValidationErrorCode.INVALID_EVENT_TYPE), "non-wire enum rejected");
        check(hasCode(EventWireValidatorV1.type("post_magic").errors(),
                DataValidationErrorCode.UNSUPPORTED_REQUIRED_ENUM), "unknown required enum stable code");
    }

    private static void contractRegistryContracts() {
        check(DataContractRegistryV1.contractIds().size() == 8, "Data contract registry size");
        check(ContractIdValidatorV1.validateUnique(DataContractRegistryV1.contractIds()).isValid(),
                "Data contract registry IDs unique");
        check(hasCode(ContractIdValidatorV1.validateUnique(
                List.of("platform-event-v1", "platform-event-v1")).errors(),
                DataValidationErrorCode.DUPLICATE_CONTRACT_ID),
                "duplicate contract ID stable code");
        check(hasCode(ContractIdValidatorV1.validate("latest").errors(),
                DataValidationErrorCode.MALFORMED_ID), "unversioned contract ID rejected");
    }

    private static void identityContracts() {
        References.SubjectRef platform = new References.SubjectRef(
                IdentityScheme.PLATFORM_SUBJECT_V1, "subject:opaque-user-10");
        References.SubjectRef legacy = new References.SubjectRef(
                IdentityScheme.LEGACY_USER_NUMERIC_V1, "user:10");
        check(new References.ActorRef(platform).value().equals("subject:opaque-user-10"),
                "platform subject can be canonical actor");
        check(!platform.equals(legacy), "identity namespaces remain distinct");
        check(!IdentityScheme.PLATFORM_SUBJECT_V1.automaticConversionAllowed(),
                "platform identity conversion forbidden");
        check(!IdentityScheme.LEGACY_USER_NUMERIC_V1.automaticConversionAllowed(),
                "legacy identity conversion forbidden");
        expectFailure(() -> new References.SubjectRef(IdentityScheme.PLATFORM_SUBJECT_V1, "user:10"));
        expectFailure(() -> new References.SubjectRef(IdentityScheme.PLATFORM_SUBJECT_V1, "10"));
        expectFailure(() -> new References.SubjectRef(IdentityScheme.LEGACY_USER_NUMERIC_V1, "user:0"));
        expectFailure(() -> new References.ActorRef(legacy));
        References.EntityRef entity = new References.EntityRef("region:KR-11");
        check(entity.entityType().equals("region"), "entity type parsed");
        check(entity.sourceId().equals("KR-11"), "entity source remains opaque");
    }

    private static void taxonomyContracts() {
        check(EventTaxonomyRegistryV1.CONTRACT_ID.equals("behavior-event-taxonomy-v1"),
                "taxonomy contract ID");
        check(EventTaxonomyRegistryV1.definitions().size() == 21, "all behavior event types registered");
        check(EventTaxonomyRegistryV1.definitions().size() == EventType.values().length,
                "registry has no missing or duplicate enum entries");
        Set<String> wires = new java.util.HashSet<>();
        for (EventType type : EventType.values()) {
            check(type.wireValue().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"),
                    "lowercase snake case: " + type.wireValue());
            check(wires.add(type.wireValue()), "unique event wire: " + type.wireValue());
            check(EventTaxonomyRegistryV1.definition(EventFamily.USER_BEHAVIOR, type).isPresent(),
                    "family/type registered: " + type.wireValue());
        }
        check(EventTaxonomyRegistryV1.definition(EventFamily.SEARCH_RUNTIME, EventType.POST_VIEW).isEmpty(),
                "invalid family/type combination rejected");
        check(EventTaxonomyRegistryV1.p0WireMappingCandidates().size() == 16, "P0 wire fixture count");
        check(EventTaxonomyRegistryV1.p0WireMappingCandidates().get("save") == EventType.POST_BOOKMARK,
                "P0 save adapter meaning protected");
        check(EventTaxonomyRegistryV1.p0WireMappingCandidates().get("impression") == EventType.RECOMMENDATION_IMPRESSION,
                "P0 impression conditional mapping preserved");
        check(EventType.fromWire("unknown_required_enum").isEmpty(), "unknown required enum fail closed");
    }

    private static void commandValidationContracts() throws IOException {
        ClientEventCommandV1 command = readCommandFixture();
        ClientEventCommandValidatorV1 validator = new ClientEventCommandValidatorV1();
        check(validator.validate(command).isValid(), "valid command fixture");
        ClientEventCommandV1 offsetless = new ClientEventCommandV1(
                command.requestedEventType(), command.entityCandidateRef(), "2026-07-21T00:00:00",
                command.sessionToken(), command.idempotencyKey(), command.context());
        check(hasCode(validator.validate(offsetless).errors(), DataValidationErrorCode.NON_UTC_TIMESTAMP),
                "offset-less datetime rejected");
        ClientEventCommandV1 unknown = new ClientEventCommandV1(
                "post_magic", command.entityCandidateRef(), command.occurredAt(),
                command.sessionToken(), command.idempotencyKey(), command.context());
        check(hasCode(validator.validate(unknown).errors(), DataValidationErrorCode.UNSUPPORTED_REQUIRED_ENUM),
                "unknown command enum rejected");
        ClientEventCommandV1 stateMutationIntent = new ClientEventCommandV1(
                "post_like", "post:123", command.occurredAt(),
                command.sessionToken(), "like-command-1", Map.of());
        check(validator.validate(stateMutationIntent).isValid(),
                "command does not require server-derived canonical stateTransitionRef");
        ClientEventCommandV1 forbidden = new ClientEventCommandV1(
                command.requestedEventType(), command.entityCandidateRef(), command.occurredAt(),
                command.sessionToken(), command.idempotencyKey(), Map.of("surface", "feed", "accessToken", "x"));
        check(hasCode(validator.validate(forbidden).errors(), DataValidationErrorCode.FORBIDDEN_PAYLOAD_FIELD),
                "forbidden payload rejected");
    }

    private static void envelopeValidationContracts() throws IOException {
        PlatformEventEnvelopeV1 event = readEnvelopeFixture();
        PlatformEventEnvelopeValidatorV1 validator = new PlatformEventEnvelopeValidatorV1();
        check(validator.validate(event).isValid(), "valid canonical envelope fixture");
        PlatformEventEnvelopeV1 invalidPair = new PlatformEventEnvelopeV1(
                event.contractVersion(), event.schemaVersion(), event.canonicalizationVersion(),
                event.producerVersion(), event.producerBuildId(), event.eventId(), EventFamily.SEARCH_RUNTIME,
                event.eventType(), event.occurredAt(), event.receivedAt(), event.actorRef(), event.sessionRef(),
                event.entityRef(), event.requestId(), event.correlationId(), event.causationId(),
                event.idempotencyKey(), event.payload());
        check(hasCode(validator.validate(invalidPair).errors(), DataValidationErrorCode.INVALID_FAMILY_TYPE_COMBINATION),
                "invalid family/type pair rejected");
        PlatformEventEnvelopeV1 invalidTime = new PlatformEventEnvelopeV1(
                event.contractVersion(), event.schemaVersion(), event.canonicalizationVersion(),
                event.producerVersion(), event.producerBuildId(), event.eventId(), event.eventFamily(),
                event.eventType(), event.receivedAt(), event.occurredAt(), event.actorRef(), event.sessionRef(),
                event.entityRef(), event.requestId(), event.correlationId(), event.causationId(),
                event.idempotencyKey(), event.payload());
        check(hasCode(validator.validate(invalidTime).errors(), DataValidationErrorCode.INVALID_TIMESTAMP),
                "receivedAt before occurredAt rejected");
    }

    private static void canonicalizationContracts() {
        CanonicalJsonNormalizerV1 normalizer = new CanonicalJsonNormalizerV1();
        Versions.CanonicalizationVersion version =
                new Versions.CanonicalizationVersion("platform-event-canonical-json-v1");

        LinkedHashMap<String, Object> first = new LinkedHashMap<>();
        first.put("z", List.of(2, 1));
        first.put("a", Map.of("b", 1.2300d, "a", true));
        first.put("timestamp", Instant.parse("2026-07-21T00:00:00Z"));
        first.put("nullable", null);

        LinkedHashMap<String, Object> second = new LinkedHashMap<>();
        second.put("nullable", null);
        second.put("timestamp", Instant.parse("2026-07-21T00:00:00Z"));
        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", true);
        nested.put("b", 1.23d);
        second.put("a", nested);
        second.put("z", List.of(2, 1));

        byte[] firstBytes = normalizer.canonicalize(new CanonicalizationRequestV1(version, first)).canonicalBytes();
        byte[] secondBytes = normalizer.canonicalize(new CanonicalizationRequestV1(version, second)).canonicalBytes();
        check(Arrays.equals(firstBytes, secondBytes), "Map insertion order independent");
        check(new String(firstBytes, java.nio.charset.StandardCharsets.UTF_8).equals(
                "{\"a\":{\"a\":true,\"b\":1.23},\"nullable\":null,\"timestamp\":\"2026-07-21T00:00:00Z\",\"z\":[2,1]}"),
                "deterministic canonical JSON");
        check(Arrays.equals(firstBytes,
                normalizer.canonicalize(new CanonicalizationRequestV1(version, first)).canonicalBytes()),
                "repeated canonical output identical");

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            byte[] changedEnvironment = normalizer
                    .canonicalize(new CanonicalizationRequestV1(version, first)).canonicalBytes();
            check(Arrays.equals(firstBytes, changedEnvironment), "locale and timezone independent");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        byte[] emptyArray = normalizer.canonicalize(
                new CanonicalizationRequestV1(version, Map.of("value", List.of()))).canonicalBytes();
        byte[] missing = normalizer.canonicalize(
                new CanonicalizationRequestV1(version, Map.of())).canonicalBytes();
        check(!Arrays.equals(emptyArray, missing), "empty array differs from missing");
        byte[] explicitNull = normalizer.canonicalize(
                new CanonicalizationRequestV1(version, mapWithNull("value"))).canonicalBytes();
        check(!Arrays.equals(explicitNull, missing), "explicit null differs from missing");
        check(!normalizer.canonicalize(new CanonicalizationRequestV1(
                version, Map.of("accessToken", "forbidden"))).isSuccess(),
                "canonicalization rejects forbidden fields");
    }

    private static void fingerprintBoundaryContracts() {
        UnresolvedEventFingerprintBoundaryV1 boundary = new UnresolvedEventFingerprintBoundaryV1();
        var result = boundary.fingerprint(new FingerprintRequestV1(
                new Versions.CanonicalizationVersion("platform-event-canonical-json-v1"),
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(result.status() == FingerprintStatus.UNRESOLVED_CONTRACT,
                "fingerprint algorithm remains unresolved");
        check(result.fingerprintValue() == null, "no temporary fingerprint emitted");
        check(hasCode(result.errors(), DataValidationErrorCode.FINGERPRINT_CONTRACT_UNRESOLVED),
                "SC-DP1-009 fail-closed code");
    }

    private static void idempotencyContracts() {
        IdempotencyComparisonV1 comparison = new IdempotencyComparisonV1();
        References.IdempotencyKey key = new References.IdempotencyKey("same-key");
        References.EventId eventId = new References.EventId("event:existing-1");
        var duplicate = comparison.compare(key, "opaque-fp-1", eventId, key, "opaque-fp-1");
        check(duplicate.isValid(), "duplicate comparison valid");
        check(duplicate.value().classification() == IdempotencyClassification.DUPLICATE,
                "same key and fingerprint duplicate");
        check(duplicate.value().existingEventId().equals(eventId), "duplicate returns existing event reference");
        var conflict = comparison.compare(key, "opaque-fp-1", eventId, key, "opaque-fp-2");
        check(conflict.value().classification() == IdempotencyClassification.CONFLICT,
                "same key different fingerprint conflict");
        check(conflict.value().errorCode() == DataValidationErrorCode.IDEMPOTENCY_CONFLICT,
                "stable conflict code");
        var fresh = comparison.compare(
                key, "opaque-fp-1", eventId, new References.IdempotencyKey("different-key"), "opaque-fp-2");
        check(fresh.value().classification() == IdempotencyClassification.NEW, "different key is new");
    }

    private static void compatibilityContracts() {
        CompatibilityEvaluatorV1 evaluator = new CompatibilityEvaluatorV1();
        check(evaluator.classify(new CompatibilityRequestV1(
                "platform-event-v1", "user-behavior-event-v1", "data-event-consumer-v1", false, false))
                == CompatibilityClassification.COMPATIBLE, "exact compatible");
        check(evaluator.classify(new CompatibilityRequestV1(
                "platform-event-v1", "user-behavior-event-v1", "data-event-consumer-v1", true, false))
                == CompatibilityClassification.COMPATIBLE_WITH_IGNORED_OPTIONAL_FIELDS,
                "unknown optional field can be explicitly ignored");
        check(evaluator.classify(new CompatibilityRequestV1(
                "platform-event-v1", "user-behavior-event-v2", "data-event-consumer-v1", false, false))
                == CompatibilityClassification.INCOMPATIBLE_SCHEMA, "unknown schema fail closed");
        check(evaluator.classify(new CompatibilityRequestV1(
                "platform-event-v1", "user-behavior-event-v1", "data-event-consumer-v1", false, true))
                == CompatibilityClassification.INCOMPATIBLE_REQUIRED_ENUM, "unknown required enum fail closed");
        check(evaluator.classify(new CompatibilityRequestV1(
                "platform-event-v1", "user-behavior-event-v1", "data-event-consumer-v2", false, false))
                == CompatibilityClassification.UNSUPPORTED_CONSUMER_VERSION, "unsupported consumer fail closed");
        check(evaluator.classify(new CompatibilityRequestV1(
                "recommendation-behavior-event-v1", "protected-p0-v1", "data-event-consumer-v1", false, false))
                == CompatibilityClassification.MIGRATION_REQUIRED, "P0 requires adapter migration");
    }

    private static void protectedFixtureContracts() throws IOException {
        String p0 = fixture("p0-recommendation-behavior-wires-v1.tsv");
        for (String wire : List.of(
                "impression", "view", "click", "like", "unlike", "save", "unsave", "share",
                "follow", "unfollow", "hide", "report", "search", "tag_click", "crew_join", "crew_leave")) {
            check(p0.contains("\t" + wire + "\t"), "P0 wire protected fixture: " + wire);
        }
        String p1 = fixture("p1-identity-version-compatibility-v1.tsv");
        check(p1.contains("subject:opaque-user-10"), "P1 platform subject fixture");
        check(p1.contains("user:10"), "P1 legacy subject fixture");
        check(p1.contains("forbidden"), "P1 automatic mapping forbidden");
        check(p1.contains("shadow_only"), "P1 Data input remains shadow-only");

        String p2 = fixture("p2-authority-compatibility-v1.tsv");
        check(p2.contains("recommendation_p2_experiment_exposure"), "P2 exposure authority fixture");
        check(p2.contains("recommendation-evaluation-dataset-v1"), "P2 dataset authority fixture");
        check(p2.contains("click,like,save,share"), "P2 engagement inclusion protected");
        check(p2.contains("view,impression,hide,report"), "P2 engagement exclusion protected");
        check(p2.contains("experiment-outcome-input-v1_shadow_only"), "P2 Data input remains shadow-only");
    }

    private static void architectureIsolation() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            sourceRoot = Path.of("jc-data-contracts/src/main/java");
        }
        List<String> forbidden = List.of(
                "org.springframework", "jakarta.persistence", "javax.persistence", "java.sql.",
                "JdbcTemplate", "EntityManager", "Instant.now(", "System.currentTimeMillis(",
                "System.getenv(", "System.getProperty(");
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String token : forbidden) {
                        check(!source.contains(token), "forbidden runtime dependency/token " + token + " in " + path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        Path digestSource = sourceRoot.resolve("com/jc/data/contract/support/Sha256DigestV1.java");
        check(Files.isRegularFile(digestSource), "SC-approved SHA-256 implementation exists");
        String digestImplementation = Files.readString(digestSource);
        check(digestImplementation.contains("MessageDigest"), "approved digest implementation is explicit");
        check(digestImplementation.contains("SHA-256"), "approved algorithm constant is explicit");
        check(!digestImplementation.contains("Base64"), "approved output is lowercase hex, not Base64");
        Path unresolvedSource = sourceRoot.resolve(
                "com/jc/data/contract/v1/fingerprint/UnresolvedEventFingerprintBoundaryV1.java");
        String unresolvedImplementation = Files.readString(unresolvedSource);
        check(!unresolvedImplementation.contains("MessageDigest"),
                "legacy unresolved boundary remains non-concrete");
        check(!unresolvedImplementation.contains("SHA-256"),
                "legacy unresolved boundary does not select an algorithm");
    }

    private static void fixtureSafety() throws IOException {
        Path fixtureRoot = Path.of("src/test/resources");
        if (!Files.isDirectory(fixtureRoot)) {
            fixtureRoot = Path.of("jc-data-contracts/src/test/resources");
        }
        List<String> forbidden = List.of(
                "refresh_token", "authorization:", "password", "secret_key", "-----begin private key");
        try (var paths = Files.walk(fixtureRoot)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String lower = Files.readString(path).toLowerCase(Locale.ROOT);
                    for (String token : forbidden) {
                        check(!lower.contains(token), "sensitive fixture token absent: " + token);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        Map<String, Object> invalid = castMap(StrictContractJsonParserV1.parse(
                fixture("platform-event-v1-invalid-forbidden-payload.json")));
        Map<String, Object> payload = castMap(invalid.get("payload"));
        check(payload.containsKey("accessToken"), "negative fixture carries forbidden field for rejection test");
    }

    private static ClientEventCommandV1 readCommandFixture() throws IOException {
        Map<String, Object> map = castMap(StrictContractJsonParserV1.parse(
                fixture("client-event-command-v1-valid.json")));
        return new ClientEventCommandV1(
                string(map, "requestedEventType"),
                nullableString(map, "entityCandidateRef"),
                string(map, "occurredAt"),
                nullableString(map, "sessionToken"),
                string(map, "idempotencyKey"),
                castMap(map.get("context")));
    }

    private static PlatformEventEnvelopeV1 readEnvelopeFixture() throws IOException {
        Map<String, Object> map = castMap(StrictContractJsonParserV1.parse(
                fixture("platform-event-v1-valid.json")));
        String causation = nullableString(map, "causationId");
        return new PlatformEventEnvelopeV1(
                new Versions.ContractVersion(string(map, "contractVersion")),
                new Versions.SchemaVersion(string(map, "schemaVersion")),
                new Versions.CanonicalizationVersion(string(map, "canonicalizationVersion")),
                new Versions.ProducerVersion(string(map, "producerVersion")),
                new Versions.ProducerBuildId(string(map, "producerBuildId")),
                new References.EventId(string(map, "eventId")),
                EventFamily.fromWire(string(map, "eventFamily")).orElseThrow(),
                EventType.fromWire(string(map, "eventType")).orElseThrow(),
                Instant.parse(string(map, "occurredAt")),
                Instant.parse(string(map, "receivedAt")),
                new References.ActorRef(new References.SubjectRef(
                        IdentityScheme.PLATFORM_SUBJECT_V1, string(map, "actorRef"))),
                new References.SessionRef(string(map, "sessionRef")),
                new References.EntityRef(string(map, "entityRef")),
                new References.RequestRef(string(map, "requestId")),
                new References.CorrelationRef(string(map, "correlationId")),
                causation == null ? null : new References.CausationRef(causation),
                new References.IdempotencyKey(string(map, "idempotencyKey")),
                castMap(map.get("payload")));
    }

    private static Map<String, Object> mapWithNull(String key) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(key, null);
        return map;
    }

    private static boolean hasCode(
            List<com.jc.data.contract.v1.validation.ValidationError> errors,
            DataValidationErrorCode code) {
        return errors.stream().anyMatch(error -> error.code() == code);
    }

    private static String fixture(String name) throws IOException {
        Path path = Path.of("src/test/resources", name);
        if (!Files.isRegularFile(path)) {
            path = Path.of("jc-data-contracts/src/test/resources", name);
        }
        return Files.readString(path);
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("fixture field must be string: " + key);
        }
        return string;
    }

    private static String nullableString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("fixture field must be string or null: " + key);
        }
        return string;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("fixture value must be object");
        }
        return (Map<String, Object>) value;
    }

    private static void expectFailure(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected failure");
        } catch (RuntimeException expected) {
            assertions++;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
