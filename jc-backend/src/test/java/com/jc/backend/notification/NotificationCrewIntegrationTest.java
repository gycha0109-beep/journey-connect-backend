package com.jc.backend.notification;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.crew.CrewDtos;
import com.jc.backend.crew.CrewMemberStatus;
import com.jc.backend.crew.CrewService;
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
class NotificationCrewIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;
    @Autowired private NotificationService notifications;

    @Test
    void crewApplicationApprovalAndRejectionProduceRecipientScopedNotifications() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("notify-owner-" + suffix, "owner_" + suffix);
        UserAccount approvedApplicant = user("notify-approved-" + suffix, "approved_" + suffix);
        UserAccount rejectedApplicant = user("notify-rejected-" + suffix, "rejected_" + suffix);
        region(regions, "KR-SEOUL");

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "notification crew",
                "KR-SEOUL",
                null,
                "notification integration",
                LocalDate.now().plusDays(10),
                4,
                true));

        CrewDtos.ApplicationView approvedApplication = crewService.join(approvedApplicant.getId(), crew.id());
        assertThat(approvedApplication.status()).isEqualTo(CrewMemberStatus.PENDING);

        NotificationDtos.Item ownerNotification = notifications.list(owner.getId(), 0, 20).items().get(0);
        assertThat(ownerNotification.type()).isEqualTo("crew_application");
        assertThat(ownerNotification.targetType()).isEqualTo("crew");
        assertThat(ownerNotification.targetId()).isEqualTo(crew.id());
        assertThat(ownerNotification.actor().id()).isEqualTo(approvedApplicant.getId());
        assertThat(ownerNotification.read()).isFalse();
        assertThat(notifications.unreadCount(owner.getId()).count()).isEqualTo(1L);

        assertThatThrownBy(() -> notifications.markRead(approvedApplicant.getId(), ownerNotification.id()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("NOTIFICATION_NOT_FOUND"));

        notifications.markRead(owner.getId(), ownerNotification.id());
        assertThat(notifications.unreadCount(owner.getId()).count()).isZero();

        crewService.review(
                owner.getId(),
                crew.id(),
                approvedApplication.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));

        NotificationDtos.Item approvalNotification = notifications
                .list(approvedApplicant.getId(), 0, 20)
                .items()
                .get(0);
        assertThat(approvalNotification.type()).isEqualTo("crew_approved");
        assertThat(approvalNotification.actor().id()).isEqualTo(owner.getId());
        assertThat(notifications.unreadCount(approvedApplicant.getId()).count()).isEqualTo(1L);

        CrewDtos.ApplicationView rejectedApplication = crewService.join(rejectedApplicant.getId(), crew.id());
        crewService.review(
                owner.getId(),
                crew.id(),
                rejectedApplication.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.REJECTED));

        NotificationDtos.Item rejectionNotification = notifications
                .list(rejectedApplicant.getId(), 0, 20)
                .items()
                .get(0);
        assertThat(rejectionNotification.type()).isEqualTo("crew_rejected");
        assertThat(rejectionNotification.actor().id()).isEqualTo(owner.getId());

        NotificationDtos.UpdateResult allRead = notifications.markAllRead(rejectedApplicant.getId());
        assertThat(allRead.updatedCount()).isEqualTo(1L);
        assertThat(notifications.unreadCount(rejectedApplicant.getId()).count()).isZero();
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
