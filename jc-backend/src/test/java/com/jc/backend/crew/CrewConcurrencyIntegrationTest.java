package com.jc.backend.crew;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.DomainException;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrewConcurrencyIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewRepository crews;
    @Autowired private CrewMemberRepository members;
    @Autowired private CrewService crewService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void simultaneousImmediateJoinRequestsCannotExceedCapacity() throws Exception {
        UserAccount owner = users.save(new UserAccount("crew-owner@example.com", "hash", "owner"));
        UserAccount first = users.save(new UserAccount("crew-first@example.com", "hash", "first"));
        UserAccount second = users.save(new UserAccount("crew-second@example.com", "hash", "second"));
        Region jeju = region(regions, "KR-JEJU");

        Crew crew = new TransactionTemplate(transactionManager).execute(status -> {
            Crew saved = crews.save(new Crew(
                    owner,
                    jeju,
                    "Jeju crew",
                    "description",
                    LocalDate.now().plusDays(10),
                    2,
                    false));
            members.save(new CrewMember(saved, owner, CrewMemberStatus.OWNER));
            return saved;
        });
        assertThat(crew).isNotNull();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        try {
            results.add(submitJoin(executor, ready, start, first.getId(), crew.getId()));
            results.add(submitJoin(executor, ready, start, second.getId(), crew.getId()));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> statuses = List.of(
                    results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder("APPROVED", "CREW_FULL");
            assertThat(members.countByCrewIdAndStatusIn(
                            crew.getId(),
                            List.of(CrewMemberStatus.OWNER, CrewMemberStatus.APPROVED)))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<String> submitJoin(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Long userId,
            Long crewId) {
        return executor.submit(() -> {
            ready.countDown();
            start.await();
            try {
                return crewService.join(userId, crewId).status().name();
            } catch (DomainException exception) {
                return exception.getCode();
            }
        });
    }
}
