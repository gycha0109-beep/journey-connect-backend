package com.jc.data.contract;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0AdapterInputV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0AdapterOutputV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0AdapterPolicyV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0BehaviorEventSourceV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0CompatibilityClass;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0EventAdapterV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0ExposureBindingV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0IdentityBindingV1;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0MappingFailure;
import com.jc.data.contract.v1.adapter.recommendation.RecommendationP0MappingStatus;
import com.jc.data.contract.v1.event.EventTaxonomyRegistryV1;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.identity.IdentityScheme;
import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.version.Versions;
import com.jc.recommendation.evaluation.RecommendationBehaviorEventResolver;
import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.event.UserBehaviorEvent;
import com.jc.recommendation.model.event.UserBehaviorEventMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class Dp4RecommendationAdapterContractTest {
    private static final Instant OCCURRED = Instant.parse("2026-07-20T12:34:56.123Z");
    private static final Instant RECEIVED = Instant.parse("2026-07-20T12:34:56.456Z");
    private static final Versions.ProducerBuildId BUILD_A =
            new Versions.ProducerBuildId("git:1111111111111111111111111111111111111111");
    private static final Versions.ProducerBuildId BUILD_B =
            new Versions.ProducerBuildId("git:2222222222222222222222222222222222222222");
    private static final RecommendationP0IdentityBindingV1 IDENTITY = new RecommendationP0IdentityBindingV1(
            42L,
            new References.ActorRef(new References.SubjectRef(
                    IdentityScheme.PLATFORM_SUBJECT_V1, "subject:dp4-fixture")),
            "recommendation-user-subject-binding-v1");

    private Dp4RecommendationAdapterContractTest() {
    }

    public static void main(String[] args) {
        boolean generate = args.length == 1 && "--generate".equals(args[0]);
        List<Fixture> fixtures = fixtures();
        RecommendationP0EventAdapterV1 adapter = new RecommendationP0EventAdapterV1();
        if (generate) {
            System.out.println("fixture_id\tsource_event_type\ttarget_event_type\tcompatibility\toutput_fingerprint");
            for (Fixture fixture : fixtures) {
                RecommendationP0AdapterOutputV1 output = adapter.adapt(fixture.input(BUILD_A));
                System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%s%n",
                        fixture.id(), fixture.source().eventTypeWire(), fixture.targetType().wireValue(),
                        output.compatibilityClass().wireValue(), output.outputFingerprint());
            }
            return;
        }

        verifyTaxonomy(fixtures);
        verifyGoldenMappings(adapter, fixtures);
        verifyRecommendationSourceCompatibility(fixtures);
        verifyDeterminism(adapter, fixtures.getFirst());
        verifyInvalidMappings(adapter, fixtures.getFirst());
        System.out.println("DP-4 recommendation adapter contract assertions: PASS");
    }

    private static void verifyTaxonomy(List<Fixture> fixtures) {
        Set<String> coreWires = java.util.Arrays.stream(com.jc.recommendation.model.event.EventType.values())
                .map(com.jc.recommendation.model.event.EventType::wireValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        check(coreWires.equals(EventTaxonomyRegistryV1.p0WireMappingCandidates().keySet()),
                "P0 taxonomy and Data mapping registry must match exactly");
        check(fixtures.size() == coreWires.size(), "every P0 event type needs a fixture");
    }

    private static void verifyGoldenMappings(
            RecommendationP0EventAdapterV1 adapter,
            List<Fixture> fixtures) {
        Map<String, String> expected = readGoldenFixture();
        for (Fixture fixture : fixtures) {
            RecommendationP0AdapterOutputV1 output = adapter.adapt(fixture.input(BUILD_A));
            check(output.isMapped(), fixture.id() + " must map");
            check(output.mappingStatus() == RecommendationP0MappingStatus.MAPPED_SHADOW,
                    fixture.id() + " must remain shadow-only");
            check(output.compatibilityClass() == RecommendationP0CompatibilityClass.SEMANTIC_COMPATIBLE,
                    fixture.id() + " must be semantic compatible");
            check(output.mappedEvent().eventType() == fixture.targetType(), fixture.id() + " target type mismatch");
            check(output.outputFingerprint().matches("[0-9a-f]{64}"), fixture.id() + " fingerprint shape");
            check(output.outputFingerprint().equals(expected.get(fixture.id())), fixture.id() + " golden fingerprint");
            check(!output.outputFingerprint().equals(fixture.source().payloadFingerprint()),
                    fixture.id() + " must not reuse source fingerprint");
        }
    }

    private static void verifyRecommendationSourceCompatibility(List<Fixture> fixtures) {
        RecommendationBehaviorEventResolver resolver = new RecommendationBehaviorEventResolver();
        for (Fixture fixture : fixtures) {
            var result = resolver.resolve(List.of(fixture.coreEvent()));
            check(result.inputCount() == 1 && result.resolvedCount() == 1 && result.duplicateCount() == 0,
                    fixture.id() + " must pass existing P0 resolver");
            String p0Signature = RecommendationBehaviorEventResolver.buildCanonicalSignature(
                    result.resolvedEvents().getFirst(), fixture.coreEvent().metadata());
            RecommendationP0AdapterOutputV1 output = new RecommendationP0EventAdapterV1()
                    .adapt(fixture.input(BUILD_A));
            check(!output.outputFingerprint().equals(p0Signature), fixture.id() + " must use separate adapter fingerprint");
        }
    }

    private static void verifyDeterminism(RecommendationP0EventAdapterV1 adapter, Fixture fixture) {
        String expected = adapter.adapt(fixture.input(BUILD_A)).outputFingerprint();
        check(expected.equals(adapter.adapt(fixture.input(BUILD_A)).outputFingerprint()), "repeated output");
        check(expected.equals(adapter.adapt(fixture.input(BUILD_B)).outputFingerprint()), "build ID exclusion");

        RecommendationP0BehaviorEventSourceV1 reordered = withMetadataOrderReversed(fixture.source());
        check(expected.equals(adapter.adapt(new RecommendationP0AdapterInputV1(
                reordered, IDENTITY, fixture.exposure(), BUILD_A)).outputFingerprint()), "map order independence");

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            check(expected.equals(adapter.adapt(fixture.input(BUILD_A)).outputFingerprint()),
                    "locale/timezone independence");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        RecommendationP0AdapterPolicyV1 policy2 = new RecommendationP0AdapterPolicyV1(
                "p0-recommendation-event-adapter-v1",
                "recommendation-p0-event-adapter-v2",
                "recommendation-p0-mapping-policy-v1",
                "recommendation-p0-adapter-output-sha256-v1",
                "recommendation-behavior-event-v1",
                "platform-event-v1",
                "user-behavior-event-v1",
                "platform-event-canonical-json-v1");
        String version2 = new RecommendationP0EventAdapterV1(policy2)
                .adapt(fixture.input(BUILD_A)).outputFingerprint();
        check(!expected.equals(version2), "adapter version must affect output fingerprint");

        RecommendationP0BehaviorEventSourceV1 changed = source(
                fixture.id(), fixture.source().eventTypeWire(), fixture.source().entityType(),
                fixture.source().sourceEntityId(), fixture.source().runId(), fixture.source().metadata(), "meaning-changed");
        String changedFingerprint = adapter.adapt(new RecommendationP0AdapterInputV1(
                changed, IDENTITY, fixture.exposure(), BUILD_A)).outputFingerprint();
        check(!expected.equals(changedFingerprint), "source meaning must affect output fingerprint");
    }

    private static void verifyInvalidMappings(RecommendationP0EventAdapterV1 adapter, Fixture basis) {
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                source("invalid-type", "unknown_type", "post", 1L, null, Map.of("surface", "detail"), "unknown"),
                IDENTITY, null, BUILD_A), RecommendationP0MappingFailure.UNSUPPORTED_EVENT_TYPE);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                copy(basis.source(), "unsupported-schema-v1", null, null, null, null),
                IDENTITY, basis.exposure(), BUILD_A), RecommendationP0MappingFailure.UNSUPPORTED_SCHEMA_VERSION);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(basis.source(), null, basis.exposure(), BUILD_A),
                RecommendationP0MappingFailure.IDENTITY_MAPPING_REQUIRED);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(basis.source(), IDENTITY, null, BUILD_A),
                RecommendationP0MappingFailure.MISSING_EXPOSURE_REFERENCE);
        RecommendationP0ExposureBindingV1 p2 = new RecommendationP0ExposureBindingV1(
                RecommendationP0AdapterPolicyV1.P2_EXPERIMENT_EXPOSURE_AUTHORITY,
                "exposure:p2", basis.source().runId(), basis.source().entityKey(), 1, 1, "home",
                "recommendation-general-exposure-binding-v1");
        expectFailure(adapter, new RecommendationP0AdapterInputV1(basis.source(), IDENTITY, p2, BUILD_A),
                RecommendationP0MappingFailure.EXPOSURE_AUTHORITY_CONFLICT);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                copy(basis.source(), null, "0000000000000000000000000000000000000000000000000000000000000000",
                        null, null, null), IDENTITY, basis.exposure(), BUILD_A),
                RecommendationP0MappingFailure.SOURCE_FINGERPRINT_MISMATCH);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                copy(basis.source(), null, null, null, OCCURRED.minusSeconds(1), null),
                IDENTITY, basis.exposure(), BUILD_A), RecommendationP0MappingFailure.TIMESTAMP_INVALID);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                source("privacy", "view", "post", 1L, null, Map.of("accessToken", "secret"), "privacy"),
                IDENTITY, null, BUILD_A), RecommendationP0MappingFailure.PRIVACY_POLICY_VIOLATION);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                source("missing-entity", "view", null, null, null, Map.of("surface", "detail"), "missing"),
                IDENTITY, null, BUILD_A), RecommendationP0MappingFailure.MISSING_REQUIRED_REFERENCE);
        expectFailure(adapter, new RecommendationP0AdapterInputV1(
                source("unmappable", "share", "post", 1L, null, Map.of("surface", "detail"), "unmappable"),
                IDENTITY, null, BUILD_A), RecommendationP0MappingFailure.PAYLOAD_UNMAPPABLE);
        RecommendationP0IdentityBindingV1 wrongIdentity = new RecommendationP0IdentityBindingV1(
                43L, IDENTITY.actorRef(), "recommendation-user-subject-binding-v1");
        expectFailure(adapter, new RecommendationP0AdapterInputV1(basis.source(), wrongIdentity, basis.exposure(), BUILD_A),
                RecommendationP0MappingFailure.IDENTITY_MAPPING_REQUIRED);
    }

    private static void expectFailure(
            RecommendationP0EventAdapterV1 adapter,
            RecommendationP0AdapterInputV1 input,
            RecommendationP0MappingFailure expected) {
        RecommendationP0AdapterOutputV1 output = adapter.adapt(input);
        check(!output.isMapped() && output.failure() == expected, "expected failure " + expected.wireValue());
        check(!output.failure().retryable(), "mapping failures must not be retried automatically");
    }

    private static List<Fixture> fixtures() {
        ArrayList<Fixture> result = new ArrayList<>();
        result.add(fixture("impression", "impression", EventType.RECOMMENDATION_IMPRESSION,
                "post", 1L, "run-impression", map("surface", "home", "position", 1), true));
        result.add(fixture("view", "view", EventType.POST_VIEW,
                "post", 2L, "run-view", map("surface", "detail", "position", 2), false));
        result.add(fixture("click", "click", EventType.RECOMMENDATION_CLICK,
                "post", 3L, "run-click", map("surface", "home", "position", 1), true));
        result.add(fixture("like", "like", EventType.POST_LIKE, "post", 4L, null, Map.of(), false));
        result.add(fixture("unlike", "unlike", EventType.POST_UNLIKE, "post", 5L, null, Map.of(), false));
        result.add(fixture("save", "save", EventType.POST_BOOKMARK, "post", 6L, null, Map.of(), false));
        result.add(fixture("unsave", "unsave", EventType.POST_UNBOOKMARK, "post", 7L, null, Map.of(), false));
        result.add(fixture("share", "share", EventType.POST_SHARE,
                "post", 8L, "run-share", map("shareChannelClass", "external", "surface", "detail"), false));
        result.add(fixture("follow", "follow", EventType.FOLLOW, "user", 9L, null, Map.of(), false));
        result.add(fixture("unfollow", "unfollow", EventType.UNFOLLOW, "user", 10L, null, Map.of(), false));
        result.add(fixture("hide", "hide", EventType.POST_HIDE,
                "post", 11L, "run-hide", map("reasonCode", "not_relevant"), false));
        result.add(fixture("report", "report", EventType.POST_REPORT,
                "post", 12L, "run-report", map("reportReasonCode", "spam", "reportRef", "report:fixture"), false));
        result.add(fixture("search", "search", EventType.SEARCH_SUBMIT,
                null, null, null, map("searchRunRef", "search_run:fixture", "queryRef", "query:fixture", "surface", "search"), false));
        result.add(fixture("tag_click", "tag_click", EventType.TAG_CLICK,
                "post", 13L, null, map("tagRef", "tag:travel", "surface", "detail"), false));
        result.add(fixture("crew_join", "crew_join", EventType.CREW_JOIN, "crew", 14L, null, Map.of(), false));
        result.add(fixture("crew_leave", "crew_leave", EventType.CREW_LEAVE, "crew", 15L, null, Map.of(), false));
        return List.copyOf(result);
    }

    private static Fixture fixture(
            String id,
            String sourceWire,
            EventType target,
            String entityType,
            Long entityId,
            String runId,
            Map<String, Object> metadata,
            boolean exposureRequired) {
        RecommendationP0BehaviorEventSourceV1 source = source(
                id, sourceWire, entityType, entityId, runId, metadata, "golden");
        RecommendationP0ExposureBindingV1 exposure = exposureRequired
                ? new RecommendationP0ExposureBindingV1(
                        RecommendationP0AdapterPolicyV1.GENERAL_EXPOSURE_AUTHORITY,
                        "exposure:" + id,
                        runId,
                        source.entityKey(),
                        100 + id.length(),
                        ((Number) metadata.get("position")).intValue(),
                        (String) metadata.get("surface"),
                        "recommendation-general-exposure-binding-v1")
                : null;
        com.jc.recommendation.model.event.EventType coreType = java.util.Arrays.stream(
                        com.jc.recommendation.model.event.EventType.values())
                .filter(value -> value.wireValue().equals(sourceWire)).findFirst().orElseThrow();
        EventSurface surface = metadata.get("surface") instanceof String value
                ? java.util.Arrays.stream(EventSurface.values())
                        .filter(candidate -> candidate.wireValue().equals(value)).findFirst().orElse(null)
                : null;
        Integer position = metadata.get("position") instanceof Number value ? value.intValue() : null;
        String query = "search".equals(sourceWire) ? "travel" : null;
        UserBehaviorEvent core = new UserBehaviorEvent(
                source.eventId(), source.idempotencyKey(), "42", source.sessionId(), coreType,
                source.entityKey(), source.runId(),
                new UserBehaviorEventMetadata(surface, position, query, null, null),
                source.occurredAt().toString());
        return new Fixture(id, source, target, exposure, core);
    }

    private static RecommendationP0BehaviorEventSourceV1 source(
            String id,
            String wire,
            String entityType,
            Long entityId,
            String runId,
            Map<String, Object> metadata,
            String canonicalVariant) {
        byte[] canonical = ("{\"fixture\":\"" + id + "\",\"variant\":\"" + canonicalVariant + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return new RecommendationP0BehaviorEventSourceV1(
                "p0-" + id,
                "idem-" + id,
                "recommendation-behavior-event-v1",
                Sha256DigestV1.lowercaseHex(canonical),
                canonical,
                42L,
                "session-source-" + id,
                runId,
                wire,
                entityType,
                entityType == null ? null : entityType + ":" + entityId,
                entityId,
                OCCURRED,
                RECEIVED,
                metadata);
    }

    private static RecommendationP0BehaviorEventSourceV1 copy(
            RecommendationP0BehaviorEventSourceV1 source,
            String schema,
            String fingerprint,
            Map<String, Object> metadata,
            Instant received,
            byte[] canonical) {
        return new RecommendationP0BehaviorEventSourceV1(
                source.eventId(), source.idempotencyKey(), schema == null ? source.schemaVersion() : schema,
                fingerprint == null ? source.payloadFingerprint() : fingerprint,
                canonical == null ? source.canonicalPayload() : canonical,
                source.userId(), source.sessionId(), source.runId(), source.eventTypeWire(),
                source.entityType(), source.entityKey(), source.sourceEntityId(), source.occurredAt(),
                received == null ? source.receivedAt() : received,
                metadata == null ? source.metadata() : metadata);
    }

    private static RecommendationP0BehaviorEventSourceV1 withMetadataOrderReversed(
            RecommendationP0BehaviorEventSourceV1 source) {
        ArrayList<Map.Entry<String, Object>> entries = new ArrayList<>(source.metadata().entrySet());
        Collections.reverse(entries);
        LinkedHashMap<String, Object> reversed = new LinkedHashMap<>();
        entries.forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
        return copy(source, null, null, reversed, null, null);
    }

    private static Map<String, Object> map(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> readGoldenFixture() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of(
                    "src/test/resources/recommendation-p0-adapter-golden-v1.tsv");
            List<String> lines = java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8);
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (int index = 1; index < lines.size(); index++) {
                String[] parts = lines.get(index).split("\\t", -1);
                result.put(parts[0], parts[4]);
            }
            return Map.copyOf(result);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("golden fixture unavailable", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Fixture(
            String id,
            RecommendationP0BehaviorEventSourceV1 source,
            EventType targetType,
            RecommendationP0ExposureBindingV1 exposure,
            UserBehaviorEvent coreEvent) {
        private RecommendationP0AdapterInputV1 input(Versions.ProducerBuildId buildId) {
            return new RecommendationP0AdapterInputV1(source, IDENTITY, exposure, buildId);
        }
    }
}
