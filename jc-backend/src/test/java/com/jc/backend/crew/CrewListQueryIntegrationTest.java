package com.jc.backend.crew;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.PageResponse;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewListQueryIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewRepository crews;
    @Autowired private CrewMemberRepository members;
    @Autowired private CrewService crewService;
    @Autowired private EntityManager entityManager;

    @Test
    void crewListLoadsOwnersRegionsAndCountsWithFixedNumberOfQueries() {
        UserAccount firstOwner = users.save(new UserAccount("list-owner1@example.com", "hash", "owner1"));
        UserAccount secondOwner = users.save(new UserAccount("list-owner2@example.com", "hash", "owner2"));
        Region seoul = region(regions, "KR-SEOUL");
        Region busan = region(regions, "KR-BUSAN");

        Crew first = crews.save(new Crew(
                firstOwner,
                seoul,
                "crew-1",
                "description",
                LocalDate.now().plusDays(3),
                5,
                true));
        Crew second = crews.save(new Crew(
                secondOwner,
                busan,
                "crew-2",
                "description",
                LocalDate.now().plusDays(4),
                5,
                true));
        members.saveAll(List.of(
                new CrewMember(first, firstOwner, CrewMemberStatus.OWNER),
                new CrewMember(second, secondOwner, CrewMemberStatus.OWNER)));

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        PageResponse<CrewDtos.View> result = crewService.list(PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(view -> {
            assertThat(view.memberCount()).isEqualTo(1);
            assertThat(view.pendingApplicationCount()).isZero();
        });
        assertThat(result.items())
                .extracting(CrewDtos.View::ownerNickname)
                .containsExactlyInAnyOrder("owner1", "owner2");

        // 크루 페이지 + count + 승인 멤버 집계 + 대기 신청 집계로 크루 수와 무관하게 고정됩니다.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
