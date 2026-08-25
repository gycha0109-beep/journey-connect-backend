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
class CrewOwnershipLimitIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;

    @Test
    void ownerCanHaveAtMostThreeRecruitingCrewsAndClosedCrewReleasesSlot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = users.save(new UserAccount(
                "limit-owner-" + suffix + "@example.com",
                "hash",
                "limit_owner_" + suffix));
        region(regions, "KR-SEOUL");

        CrewDtos.View first = create(owner, "crew-1");
        create(owner, "crew-2");
        create(owner, "crew-3");

        assertThatThrownBy(() -> create(owner, "crew-4"))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_OWNER_ACTIVE_LIMIT_EXCEEDED"));

        crewService.closeRecruitment(owner.getId(), first.id());
        CrewDtos.View replacement = create(owner, "crew-4");
        assertThat(replacement.recruiting()).isTrue();
    }

    private CrewDtos.View create(UserAccount owner, String title) {
        return crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                title,
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                4,
                true));
    }
}
