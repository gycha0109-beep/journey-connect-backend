package com.jc.backend.crew;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.PageResponse;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewProductReadModelIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;

    @Test
    void searchViewerMyCrewAndMemberReadModelsStayConsistent() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("read-owner-" + suffix, "read_owner_" + suffix);
        UserAccount applicant = user("read-applicant-" + suffix, "read_app_" + suffix);
        region(regions, "KR-SEOUL");
        region(regions, "KR-BUSAN");

        CrewDtos.View seoulCrew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "Coffee walk",
                "KR-SEOUL",
                null,
                "Seoul cafe hopping",
                LocalDate.now().plusDays(7),
                4,
                true));
        crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "Beach trip",
                "KR-BUSAN",
                null,
                "Busan coast",
                LocalDate.now().plusDays(8),
                4,
                true));

        PageResponse<CrewDtos.View> keywordSearch = crewService.list(
                null,
                "coffee",
                null,
                PageRequest.of(0, 20));
        assertThat(keywordSearch.items())
                .extracting(CrewDtos.View::id)
                .containsExactly(seoulCrew.id());

        PageResponse<CrewDtos.View> regionSearch = crewService.list(
                null,
                null,
                "KR-SEOUL",
                PageRequest.of(0, 20));
        assertThat(regionSearch.items())
                .extracting(CrewDtos.View::id)
                .containsExactly(seoulCrew.id());

        CrewDtos.ApplicationView pending = crewService.join(applicant.getId(), seoulCrew.id());
        assertThat(pending.status()).isEqualTo(CrewMemberStatus.PENDING);

        CrewDtos.View applicantView = crewService.detail(applicant.getId(), seoulCrew.id());
        assertThat(applicantView.viewer().membershipStatus()).isEqualTo(CrewMemberStatus.PENDING);
        assertThat(applicantView.viewer().owner()).isFalse();
        assertThat(applicantView.viewer().canJoin()).isFalse();
        assertThat(applicantView.viewer().canCancel()).isTrue();
        assertThat(applicantView.viewer().canManageApplications()).isFalse();

        CrewDtos.View ownerView = crewService.detail(owner.getId(), seoulCrew.id());
        assertThat(ownerView.viewer().membershipStatus()).isEqualTo(CrewMemberStatus.OWNER);
        assertThat(ownerView.viewer().owner()).isTrue();
        assertThat(ownerView.viewer().canManageApplications()).isTrue();

        PageResponse<CrewDtos.MyCrewItem> pendingMyCrews = crewService.myCrews(
                applicant.getId(),
                PageRequest.of(0, 20));
        assertThat(pendingMyCrews.items()).hasSize(1);
        assertThat(pendingMyCrews.items().getFirst().membershipStatus())
                .isEqualTo(CrewMemberStatus.PENDING);

        crewService.review(
                owner.getId(),
                seoulCrew.id(),
                pending.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));

        PageResponse<CrewDtos.MemberView> crewMembers = crewService.members(
                seoulCrew.id(),
                PageRequest.of(0, 20));
        assertThat(crewMembers.items()).hasSize(2);
        assertThat(crewMembers.items())
                .extracting(CrewDtos.MemberView::role)
                .containsExactly(CrewDtos.MemberRole.OWNER, CrewDtos.MemberRole.MEMBER);

        PageResponse<CrewDtos.MyCrewItem> approvedMyCrews = crewService.myCrews(
                applicant.getId(),
                PageRequest.of(0, 20));
        assertThat(approvedMyCrews.items().getFirst().membershipStatus())
                .isEqualTo(CrewMemberStatus.APPROVED);
        assertThat(approvedMyCrews.items().getFirst().joinedOrAppliedAt()).isNotNull();
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
