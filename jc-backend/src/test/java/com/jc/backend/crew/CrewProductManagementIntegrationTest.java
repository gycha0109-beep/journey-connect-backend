package com.jc.backend.crew;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewProductManagementIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;

    @Test
    void ownerCanUpdateCrewButCannotShrinkCapacityBelowActiveMembers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("manage-owner-" + suffix, "owner_" + suffix);
        UserAccount firstMember = user("manage-member1-" + suffix, "member1_" + suffix);
        UserAccount secondMember = user("manage-member2-" + suffix, "member2_" + suffix);
        UserAccount outsider = user("manage-outsider-" + suffix, "outsider_" + suffix);
        region(regions, "KR-SEOUL");
        region(regions, "KR-BUSAN");

        CrewDtos.View created = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "before title",
                "KR-SEOUL",
                null,
                "before description",
                LocalDate.now().plusDays(10),
                4,
                false));
        crewService.join(firstMember.getId(), created.id());
        crewService.join(secondMember.getId(), created.id());

        assertThatThrownBy(() -> crewService.update(
                owner.getId(),
                created.id(),
                new CrewDtos.UpdateRequest(null, null, null, null, null, 2)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_CAPACITY_BELOW_ACTIVE_MEMBERS"));

        assertThatThrownBy(() -> crewService.update(
                outsider.getId(),
                created.id(),
                new CrewDtos.UpdateRequest("blocked", null, null, null, null, null)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_OWNER_REQUIRED"));

        LocalDate changedDate = LocalDate.now().plusDays(20);
        CrewDtos.View updated = crewService.update(owner.getId(), created.id(), new CrewDtos.UpdateRequest(
                "after title",
                "KR-BUSAN",
                null,
                "after description",
                changedDate,
                5));

        assertThat(updated.title()).isEqualTo("after title");
        assertThat(updated.regionCode()).isEqualTo("KR-BUSAN");
        assertThat(updated.description()).isEqualTo("after description");
        assertThat(updated.travelDate()).isEqualTo(changedDate);
        assertThat(updated.capacity()).isEqualTo(5);
        assertThat(updated.memberCount()).isEqualTo(3);
        assertThat(updated.viewer().owner()).isTrue();
        assertThat(updated.viewer().canManageApplications()).isTrue();
    }

    @Test
    void closeAndReopenRespectCapacityAndTravelDatePolicies() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("lifecycle-owner-" + suffix, "life_owner_" + suffix);
        UserAccount member = user("lifecycle-member-" + suffix, "life_member_" + suffix);
        region(regions, "KR-SEOUL");

        CrewDtos.View fullCrew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "full crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(5),
                2,
                false));
        crewService.join(member.getId(), fullCrew.id());
        assertThat(crewService.closeRecruitment(owner.getId(), fullCrew.id()).recruiting()).isFalse();
        assertThatThrownBy(() -> crewService.reopenRecruitment(owner.getId(), fullCrew.id()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_FULL"));

        CrewDtos.View datedCrew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "dated crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(5),
                3,
                true));
        crewService.closeRecruitment(owner.getId(), datedCrew.id());
        crewService.update(owner.getId(), datedCrew.id(), new CrewDtos.UpdateRequest(
                null,
                null,
                null,
                null,
                LocalDate.now().minusDays(1),
                null));

        assertThatThrownBy(() -> crewService.reopenRecruitment(owner.getId(), datedCrew.id()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_TRAVEL_DATE_PASSED"));

        CrewDtos.View outsiderView = crewService.detail(member.getId(), datedCrew.id());
        assertThat(outsiderView.viewer().canJoin()).isFalse();
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
