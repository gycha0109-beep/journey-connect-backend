package com.jc.backend.crew;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.recommendation.p1.RecommendationP1ProfileSource;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.profile.BehaviorProfileBuilder;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicies;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.BuildBehaviorProfileInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
@Tag("p1-verification")
class CrewRecommendationFeedbackIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;
    @Autowired private RecommendationP1ProfileSource profileSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void approvedJoinIsAtomicIdempotentAndVisibleAsP1RegionFeedback() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("pf3-owner-" + suffix, "pf3_owner_" + suffix);
        UserAccount applicant = user("pf3-applicant-" + suffix, "pf3_applicant_" + suffix);
        region(regions, "KR-SEOUL");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "PF3 approval crew",
                "KR-SEOUL",
                null,
                "approved join feedback",
                LocalDate.now().plusDays(10),
                4,
                true));

        CrewDtos.ApplicationView pending = crewService.join(applicant.getId(), crew.id());
        assertThat(pending.status()).isEqualTo(CrewMemberStatus.PENDING);
        assertThat(eventCount(applicant.getId(), crew.id())).isZero();

        CrewDtos.ApplicationView approved = crewService.review(
                owner.getId(),
                crew.id(),
                pending.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));
        assertThat(approved.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(approved.reviewedAt()).isNotNull();
        assertThat(eventCount(applicant.getId(), crew.id())).isEqualTo(1L);

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                """
                select event_id, idempotency_key, schema_version, event_type,
                       entity_type, entity_key, source_entity_id, user_id,
                       run_id, occurred_at, metadata::text metadata
                from public.recommendation_behavior_event
                where event_id = ?
                """,
                eventId(applicant.getId(), crew.id()));
        assertThat(persisted.get("event_id")).isEqualTo(eventId(applicant.getId(), crew.id()));
        assertThat(persisted.get("idempotency_key")).isEqualTo(eventId(applicant.getId(), crew.id()));
        assertThat(persisted.get("schema_version")).isEqualTo("crew-recommendation-feedback-v1");
        assertThat(persisted.get("event_type")).isEqualTo("crew_join");
        assertThat(persisted.get("entity_type")).isEqualTo("crew");
        assertThat(persisted.get("entity_key")).isEqualTo("crew:" + crew.id());
        assertThat(((Number) persisted.get("source_entity_id")).longValue()).isEqualTo(crew.id());
        assertThat(((Number) persisted.get("user_id")).longValue()).isEqualTo(applicant.getId());
        assertThat(persisted.get("run_id")).isNull();
        assertThat(persisted.get("metadata").toString())
                .contains("approved_join", "crew-join-positive-only-v1");

        CrewDtos.ApplicationView duplicateJoin = crewService.join(applicant.getId(), crew.id());
        assertThat(duplicateJoin.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(eventCount(applicant.getId(), crew.id())).isEqualTo(1L);

        var events = profileSource.findBehaviorEvents(
                applicant.getId(),
                approved.reviewedAt().minusSeconds(1),
                approved.reviewedAt().plusSeconds(1),
                100);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventType().wireValue()).isEqualTo("crew_join");
        assertThat(events.getFirst().featureIds()).containsExactly("region:seoul");

        BehaviorProfileSnapshot profile = new BehaviorProfileBuilder().build(new BuildBehaviorProfileInput(
                Long.toString(applicant.getId()),
                approved.reviewedAt().plusSeconds(1),
                List.of(),
                events,
                BehaviorProfilePolicies.V1));
        assertThat(profile.acceptedEventCount()).isEqualTo(1);
        assertThat(profile.ignoredEventCount()).isZero();
        assertThat(profile.signals()).anySatisfy(signal -> {
            assertThat(signal.featureId()).isEqualTo("region:seoul");
            assertThat(signal.direction()).isEqualTo(PreferenceKind.PREFER);
            assertThat(signal.signedWeight()).isPositive();
        });

        crewService.cancelJoin(applicant.getId(), crew.id());
        CrewDtos.ApplicationView reapplied = crewService.join(applicant.getId(), crew.id());
        assertThat(reapplied.status()).isEqualTo(CrewMemberStatus.PENDING);
        CrewDtos.ApplicationView reapproved = crewService.review(
                owner.getId(),
                crew.id(),
                reapplied.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));
        assertThat(reapproved.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(reapproved.reviewedAt()).isAfterOrEqualTo(approved.reviewedAt());
        assertThat(eventCount(applicant.getId(), crew.id())).isEqualTo(1L);
    }

    @Test
    void pendingAndRejectedApplicationsDoNotProducePositiveFeedback() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("pf3-reject-owner-" + suffix, "pf3_reject_owner_" + suffix);
        UserAccount applicant = user("pf3-reject-applicant-" + suffix, "pf3_reject_applicant_" + suffix);
        region(regions, "KR-BUSAN");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "PF3 rejection crew",
                "KR-BUSAN",
                null,
                "rejected joins are not positive feedback",
                LocalDate.now().plusDays(12),
                4,
                true));
        CrewDtos.ApplicationView pending = crewService.join(applicant.getId(), crew.id());
        assertThat(eventCount(applicant.getId(), crew.id())).isZero();

        CrewDtos.ApplicationView rejected = crewService.review(
                owner.getId(),
                crew.id(),
                pending.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.REJECTED));
        assertThat(rejected.status()).isEqualTo(CrewMemberStatus.REJECTED);
        assertThat(eventCount(applicant.getId(), crew.id())).isZero();
    }

    @Test
    void directAutoApprovalProducesTheSameCrewJoinFeedbackContract() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("pf3-auto-owner-" + suffix, "pf3_auto_owner_" + suffix);
        UserAccount applicant = user("pf3-auto-applicant-" + suffix, "pf3_auto_applicant_" + suffix);
        region(regions, "KR-JEJU");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "PF3 auto crew",
                "KR-JEJU",
                null,
                "auto approval feedback",
                LocalDate.now().plusDays(15),
                4,
                false));
        CrewDtos.ApplicationView approved = crewService.join(applicant.getId(), crew.id());

        assertThat(approved.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(eventCount(applicant.getId(), crew.id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select event_type from public.recommendation_behavior_event where event_id = ?",
                String.class,
                eventId(applicant.getId(), crew.id())))
                .isEqualTo("crew_join");
    }

    private long eventCount(long userId, long crewId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_behavior_event where event_id = ?",
                Long.class,
                eventId(userId, crewId));
        return count == null ? 0L : count;
    }

    private static String eventId(long userId, long crewId) {
        return "crew-join-v1:" + userId + ":" + crewId;
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
