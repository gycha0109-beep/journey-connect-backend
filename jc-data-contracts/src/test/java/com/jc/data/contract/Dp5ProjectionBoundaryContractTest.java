package com.jc.data.contract;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.projection.AdapterEvidenceState;
import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ExperimentOutcomeProjectionEngine;
import com.jc.data.contract.v1.projection.IdentityBinding;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionFailureCode;
import com.jc.data.contract.v1.projection.ProjectionIdentifiers;
import com.jc.data.contract.v1.projection.ProjectionResult;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import com.jc.data.contract.v1.projection.RecommendationProfileInputProjection;
import com.jc.data.contract.v1.projection.RecommendationProfileProjectionEngine;
import com.jc.data.contract.v1.projection.SourceCheckpoint;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dp5ProjectionBoundaryContractTest {
    private static final Instant AS_OF = Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-07-22T00:05:00Z");
    private static final String BUILD = "git:3333333333333333333333333333333333333333";
    private static final String BINDING_VERSION = "recommendation-user-subject-binding-v1";
    private static final IdentityBinding IDENTITY = new IdentityBinding(
            "user:42", "subject:dp5-boundary", BINDING_VERSION,
            "approved-binding-input", hex("identity-42"), "journey-connect");

    private Dp5ProjectionBoundaryContractTest() {
    }

    public static void main(String[] args) {
        invalidTimestampOrderingIsRejected();
        conflictingIdentityBindingsFailClosed();
        outcomeAfterProjectionAsOfIsRejected();
        System.out.println("DP-5 projection boundary assertions: PASS");
    }

    private static void invalidTimestampOrderingIsRejected() {
        expectIllegalArgument(() -> source(
                "event:invalid-time", "post_like", AS_OF, AS_OF.minusSeconds(1),
                "user:42", null, null, AdapterEvidenceState.NONE, null, null, "invalid-time"));
    }

    private static void conflictingIdentityBindingsFailClosed() {
        ProjectionSourceEvent source = source(
                "event:identity", "post_like", AS_OF.minusSeconds(60), AS_OF.minusSeconds(59),
                "user:42", null, null, AdapterEvidenceState.NONE, null, null, "identity-source");
        SourceCheckpoint checkpoint = checkpoint(
                "checkpoint:identity-boundary", AS_OF.minusSeconds(3600), AS_OF, AS_OF, List.of(source));
        IdentityBinding conflicting = new IdentityBinding(
                "user:42", "subject:other", BINDING_VERSION,
                "approved-binding-input", hex("identity-conflict"), "journey-connect");
        ProjectionResult<RecommendationProfileInputProjection> result =
                new RecommendationProfileProjectionEngine().project(
                        ProjectionDefinition.profileV1(BINDING_VERSION), checkpoint, List.of(source),
                        List.of(IDENTITY, conflicting), AS_OF,
                        new ProjectionIdentifiers("projection_run:identity-boundary", "snapshot:identity-boundary"),
                        BUILD, CREATED);
        expect(result, ProjectionFailureCode.IDENTITY_NAMESPACE_CONFLICT);
    }

    private static void outcomeAfterProjectionAsOfIsRejected() {
        Instant exposedAt = AS_OF.minusSeconds(3600);
        ProjectionSourceEvent exposureSource = source(
                "event:boundary-exposure", "experiment_exposure", exposedAt, exposedAt.plusSeconds(1),
                "user:42", "exposure:boundary", "control", AdapterEvidenceState.NONE, null, null,
                "boundary-exposure");
        ProjectionSourceEvent futureOutcome = source(
                "event:boundary-future", "recommendation_click", AS_OF.plusSeconds(60), AS_OF.plusSeconds(61),
                "subject:dp5-boundary", "exposure:boundary", "control", AdapterEvidenceState.MAPPED,
                "adapter_output:boundary-future", "recommendation-p0-mapping-policy-v1", "boundary-future");
        List<ProjectionSourceEvent> sources = List.of(exposureSource, futureOutcome);
        SourceCheckpoint checkpoint = checkpoint(
                "checkpoint:outcome-boundary", exposedAt.minusSeconds(1), AS_OF.plusSeconds(120),
                AS_OF.plusSeconds(121), sources);
        ExperimentExposureBinding exposure = new ExperimentExposureBinding(
                ExperimentExposureBinding.AUTHORITY, "experiment:ranking", "experiment-ranking-v1",
                "assignment:boundary", "exposure:boundary", "recommendation_run:boundary", "user:42",
                "subject:dp5-boundary", "session:boundary", "control", exposedAt,
                hex("boundary-exposure-fingerprint"), false);
        ProjectionResult<ExperimentOutcomeInputProjection> result =
                new ExperimentOutcomeProjectionEngine().project(
                        ProjectionDefinition.outcomeV1(BINDING_VERSION), checkpoint, sources, IDENTITY, exposure,
                        AS_OF, new ProjectionIdentifiers(
                                "projection_run:outcome-boundary", "snapshot:outcome-boundary"), BUILD, CREATED);
        expect(result, ProjectionFailureCode.SOURCE_CHECKPOINT_INVALID);
    }

    private static ProjectionSourceEvent source(
            String ref,
            String type,
            Instant occurredAt,
            Instant ingestedAt,
            String identityRef,
            String exposureRef,
            String variantRef,
            AdapterEvidenceState adapterState,
            String adapterRef,
            String mappingPolicy,
            String canonical) {
        return new ProjectionSourceEvent(
                ref, hex(canonical), "platform-event-v1", "user-behavior-event-v1", type,
                occurredAt, ingestedAt, identityRef, "session:boundary", "post:1", null, "post:1",
                List.of(), exposureRef, variantRef, adapterState, adapterRef, mappingPolicy, Map.of(), canonical);
    }

    private static SourceCheckpoint checkpoint(
            String ref,
            Instant from,
            Instant to,
            Instant ingestionUpperBound,
            List<ProjectionSourceEvent> sources) {
        return SourceCheckpoint.create(
                ref, "data-platform-event-v1", "platform-event-v1", "user-behavior-event-v1",
                from, to, ingestionUpperBound, sources);
    }

    private static String hex(String value) {
        return Sha256DigestV1.lowercaseHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void expect(ProjectionResult<?> result, ProjectionFailureCode code) {
        check(!result.isSuccess() && result.failure().code() == code, "expected failure " + code.wireValue());
    }

    private static void expectIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
