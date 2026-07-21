package com.jc.data.contract;

import com.jc.data.contract.v1.event.EventFamily;
import com.jc.data.contract.v1.event.EventType;
import com.jc.data.contract.v1.event.PlatformEventEnvelopeV1;
import com.jc.data.contract.v1.fingerprint.FingerprintRequestV1;
import com.jc.data.contract.v1.fingerprint.FingerprintStatus;
import com.jc.data.contract.v1.fingerprint.PlatformEventFingerprintCanonicalizerV1;
import com.jc.data.contract.v1.fingerprint.Sha256EventFingerprintBoundaryV1;
import com.jc.data.contract.v1.fingerprint.UnresolvedEventFingerprintBoundaryV1;
import com.jc.data.contract.v1.identity.IdentityScheme;
import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.version.Versions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class Dp2FingerprintContractTest {
    private static final String EXPECTED_CANONICAL_JSON =
            "{\"actorRef\":\"subject:dp2-fingerprint-user\","
                    + "\"canonicalizationVersion\":\"platform-event-canonical-json-v1\","
                    + "\"causationId\":null,\"contractVersion\":\"platform-event-v1\","
                    + "\"entityRef\":\"post:123\",\"eventFamily\":\"user_behavior\","
                    + "\"eventType\":\"post_view\",\"occurredAt\":\"2026-07-22T00:00:00Z\","
                    + "\"payload\":{\"surface\":\"feed\",\"viewEpisodeRef\":\"view-episode:1\"},"
                    + "\"schemaVersion\":\"user-behavior-event-v1\","
                    + "\"sessionRef\":\"session:dp2-fingerprint-session\"}";
    private static final String EXPECTED_FINGERPRINT =
            "f6ca2be46c46150e7c26d82e5c22974b1fd53dc54b500a0fccba29e23450f09d";
    private static int assertions;

    private Dp2FingerprintContractTest() {
    }

    public static void main(String[] args) throws Exception {
        exactContract();
        exclusionContract();
        inclusionContract();
        determinismContract();
        compatibilityBoundary();
        fixtureContract();
        System.out.println("DP-2 fingerprint contract checks passed: " + assertions);
    }

    private static void exactContract() {
        PlatformEventEnvelopeV1 event = event(orderedPayload("feed", "view-episode:1"));
        var canonicalizer = new PlatformEventFingerprintCanonicalizerV1();
        var canonical = canonicalizer.canonicalize(event);
        check(canonical.isSuccess(), "approved fingerprint canonicalization succeeds");
        check(canonical.utf8().equals(EXPECTED_CANONICAL_JSON), "exact approved inclusion bytes");

        var result = new Sha256EventFingerprintBoundaryV1().fingerprint(new FingerprintRequestV1(
                event.canonicalizationVersion(), canonical.canonicalBytes()));
        check(result.status() == FingerprintStatus.SUCCESS, "approved fingerprint succeeds");
        check(result.fingerprintValue().equals(EXPECTED_FINGERPRINT), "known SHA-256 fixture");
        check(result.fingerprintValue().matches("[0-9a-f]{64}"), "lowercase hexadecimal length 64");
        check(Sha256EventFingerprintBoundaryV1.FINGERPRINT_VERSION
                .equals("platform-event-fingerprint-sha256-v1"), "wire version fixed");
    }

    private static void exclusionContract() {
        PlatformEventEnvelopeV1 base = event(orderedPayload("feed", "view-episode:1"));
        PlatformEventEnvelopeV1 retry = new PlatformEventEnvelopeV1(
                base.contractVersion(), base.schemaVersion(), base.canonicalizationVersion(),
                new Versions.ProducerVersion("alternate-event-producer-v1"),
                new Versions.ProducerBuildId("git:ffffffffffffffffffffffffffffffffffffffff"),
                new References.EventId("event:dp2-retry-candidate"), base.eventFamily(), base.eventType(),
                base.occurredAt(), Instant.parse("2026-07-22T00:00:30Z"), base.actorRef(), base.sessionRef(),
                base.entityRef(), new References.RequestRef("request:dp2-retry"),
                new References.CorrelationRef("correlation:dp2-retry"), base.causationId(),
                new References.IdempotencyKey("dp2-retry-key"), base.payload());
        check(fingerprint(base).equals(fingerprint(retry)),
                "eventId receivedAt producer version/build request correlation and idempotency are excluded");
    }

    private static void inclusionContract() {
        PlatformEventEnvelopeV1 base = event(orderedPayload("feed", "view-episode:1"));
        PlatformEventEnvelopeV1 occurredChanged = new PlatformEventEnvelopeV1(
                base.contractVersion(), base.schemaVersion(), base.canonicalizationVersion(),
                base.producerVersion(), base.producerBuildId(), new References.EventId("event:dp2-occurred-change"),
                base.eventFamily(), base.eventType(), Instant.parse("2026-07-22T00:00:01Z"),
                Instant.parse("2026-07-22T00:00:01.100Z"), base.actorRef(), base.sessionRef(),
                base.entityRef(), base.requestId(), base.correlationId(), base.causationId(),
                base.idempotencyKey(), base.payload());
        PlatformEventEnvelopeV1 payloadChanged = new PlatformEventEnvelopeV1(
                base.contractVersion(), base.schemaVersion(), base.canonicalizationVersion(),
                base.producerVersion(), base.producerBuildId(), new References.EventId("event:dp2-payload-change"),
                base.eventFamily(), base.eventType(), base.occurredAt(), base.receivedAt(),
                base.actorRef(), base.sessionRef(), base.entityRef(), base.requestId(), base.correlationId(),
                base.causationId(), base.idempotencyKey(), orderedPayload("detail", "view-episode:1"));
        check(!fingerprint(base).equals(fingerprint(occurredChanged)), "occurredAt is included");
        check(!fingerprint(base).equals(fingerprint(payloadChanged)), "payload meaning is included");
    }

    private static void determinismContract() {
        LinkedHashMap<String, Object> first = orderedPayload("feed", "view-episode:1");
        LinkedHashMap<String, Object> second = new LinkedHashMap<>();
        second.put("viewEpisodeRef", "view-episode:1");
        second.put("surface", "feed");
        check(fingerprint(event(first)).equals(fingerprint(event(second))), "Map insertion order independent");

        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            String baseline = fingerprint(event(first));
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            check(baseline.equals(fingerprint(event(first))), "locale and timezone independent");
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }

        byte[] bytes = EXPECTED_CANONICAL_JSON.getBytes(StandardCharsets.UTF_8);
        check(Arrays.equals(bytes, canonicalBytes(event(second))), "whitespace-free deterministic canonical bytes");
    }

    private static void compatibilityBoundary() {
        var unresolved = new UnresolvedEventFingerprintBoundaryV1().fingerprint(new FingerprintRequestV1(
                new Versions.CanonicalizationVersion("platform-event-canonical-json-v1"),
                "{}".getBytes(StandardCharsets.UTF_8)));
        check(unresolved.status() == FingerprintStatus.UNRESOLVED_CONTRACT,
                "legacy unresolved boundary remains explicit and compatible");

        var invalid = new Sha256EventFingerprintBoundaryV1().fingerprint(new FingerprintRequestV1(
                new Versions.CanonicalizationVersion("other-canonical-json-v1"),
                "{}".getBytes(StandardCharsets.UTF_8)));
        check(invalid.status() == FingerprintStatus.INVALID_REQUEST,
                "approved boundary rejects another canonicalization contract");
    }

    private static void fixtureContract() throws Exception {
        Path path = Path.of("src/test/resources/platform-event-fingerprint-sha256-v1.tsv");
        if (!Files.isRegularFile(path)) {
            path = Path.of("jc-data-contracts/src/test/resources/platform-event-fingerprint-sha256-v1.tsv");
        }
        String fixture = Files.readString(path);
        for (String included : new String[] {
                "contractVersion", "schemaVersion", "canonicalizationVersion", "eventFamily", "eventType",
                "occurredAt", "actorRef", "sessionRef", "entityRef", "causationId", "payload"}) {
            check(fixture.contains(included + "\tincluded\t"), "included fixture field: " + included);
        }
        for (String excluded : new String[] {
                "eventId", "receivedAt", "producerVersion", "producerBuildId", "requestId",
                "correlationId", "idempotencyKey"}) {
            check(fixture.contains(excluded + "\texcluded\t"), "excluded fixture field: " + excluded);
        }
    }

    private static String fingerprint(PlatformEventEnvelopeV1 event) {
        byte[] bytes = canonicalBytes(event);
        var result = new Sha256EventFingerprintBoundaryV1().fingerprint(
                new FingerprintRequestV1(event.canonicalizationVersion(), bytes));
        if (result.status() != FingerprintStatus.SUCCESS) {
            throw new AssertionError("fingerprint failed: " + result.errors());
        }
        return result.fingerprintValue();
    }

    private static byte[] canonicalBytes(PlatformEventEnvelopeV1 event) {
        var result = new PlatformEventFingerprintCanonicalizerV1().canonicalize(event);
        if (!result.isSuccess()) {
            throw new AssertionError("canonicalization failed: " + result.errors());
        }
        return result.canonicalBytes();
    }

    private static PlatformEventEnvelopeV1 event(Map<String, Object> payload) {
        return new PlatformEventEnvelopeV1(
                new Versions.ContractVersion("platform-event-v1"),
                new Versions.SchemaVersion("user-behavior-event-v1"),
                new Versions.CanonicalizationVersion("platform-event-canonical-json-v1"),
                new Versions.ProducerVersion("jc-backend-event-producer-v1"),
                new Versions.ProducerBuildId("git:0123456789abcdef0123456789abcdef01234567"),
                new References.EventId("event:dp2-fingerprint-fixture"),
                EventFamily.USER_BEHAVIOR,
                EventType.POST_VIEW,
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00.100Z"),
                new References.ActorRef(new References.SubjectRef(
                        IdentityScheme.PLATFORM_SUBJECT_V1, "subject:dp2-fingerprint-user")),
                new References.SessionRef("session:dp2-fingerprint-session"),
                new References.EntityRef("post:123"),
                new References.RequestRef("request:dp2-fingerprint"),
                new References.CorrelationRef("correlation:dp2-fingerprint"),
                null,
                new References.IdempotencyKey("dp2-fingerprint-key"),
                payload);
    }

    private static LinkedHashMap<String, Object> orderedPayload(
            String surface,
            String viewEpisodeRef) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("surface", surface);
        payload.put("viewEpisodeRef", viewEpisodeRef);
        return payload;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
