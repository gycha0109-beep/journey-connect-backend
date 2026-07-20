package com.jc.backend.crew;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.PageResponse;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewApprovalIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;

    @Test
    void approvalRequiredCrewKeepsApplicantPendingUntilOwnerApproves() {
        UserAccount owner = users.save(new UserAccount("approval-owner@example.com", "hash", "approval-owner"));
        UserAccount applicant = users.save(new UserAccount("approval-user@example.com", "hash", "approval-user"));
        region(regions, "KR-SEOUL");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "approval crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                4,
                true));

        CrewDtos.ApplicationView pending = crewService.join(applicant.getId(), crew.id());
        assertThat(pending.status()).isEqualTo(CrewMemberStatus.PENDING);

        PageResponse<CrewDtos.ApplicationView> applications =
                crewService.applications(owner.getId(), crew.id(), PageRequest.of(0, 20));
        assertThat(applications.items()).extracting(CrewDtos.ApplicationView::id)
                .containsExactly(pending.id());

        CrewDtos.ApplicationView approved = crewService.review(
                owner.getId(),
                crew.id(),
                pending.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));
        assertThat(approved.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(crewService.detail(crew.id()).memberCount()).isEqualTo(2);
    }
    @Test
    void openCrewAutoApprovalPersistsReviewerLifecycle() {
        UserAccount owner = users.save(new UserAccount(
                "open-owner@example.com", "hash", "open-owner"));
        UserAccount applicant = users.save(new UserAccount(
                "open-applicant@example.com", "hash", "open-applicant"));
        region(regions, "KR-SEOUL");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "open crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(5),
                3,
                false));

        CrewDtos.ApplicationView joined = crewService.join(applicant.getId(), crew.id());

        assertThat(joined.status()).isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(joined.reviewedBy()).isEqualTo(owner.getId());
        assertThat(joined.reviewedAt()).isNotNull();
    }

}
