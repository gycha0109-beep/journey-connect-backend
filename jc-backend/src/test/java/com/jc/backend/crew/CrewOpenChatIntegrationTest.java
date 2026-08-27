package com.jc.backend.crew;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@CanonicalPostgresTest
class CrewOpenChatIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;

    @Test
    void disclosureFollowsOwnerApprovedOnlyAcrossListDetailAndMyCrews() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("chat-owner-" + suffix, "chat_owner_" + suffix);
        UserAccount approved = user("chat-approved-" + suffix, "chat_approved_" + suffix);
        UserAccount rejected = user("chat-rejected-" + suffix, "chat_rejected_" + suffix);
        UserAccount cancelled = user("chat-cancelled-" + suffix, "chat_cancelled_" + suffix);
        UserAccount outsider = user("chat-outsider-" + suffix, "chat_outsider_" + suffix);
        region(regions, "KR-SEOUL");
        String openChatUrl = "https://open.kakao.com/o/" + suffix;

        CrewDtos.View created = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "open chat crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                6,
                true,
                openChatUrl));

        assertVisible(created, openChatUrl);
        assertVisible(crewService.detail(owner.getId(), created.id()), openChatUrl);
        assertVisible(find(crewService.list(owner.getId(), PageRequest.of(0, 20)), created.id()), openChatUrl);
        assertVisible(crewService.myCrews(owner.getId(), PageRequest.of(0, 20)).items().getFirst().crew(), openChatUrl);

        CrewDtos.View anonymousDetail = crewService.detail(created.id());
        assertThat(anonymousDetail.openChatUrl()).isNull();
        assertThat(anonymousDetail.viewer()).isNull();
        CrewDtos.View anonymousList = find(crewService.list(PageRequest.of(0, 20)), created.id());
        assertThat(anonymousList.openChatUrl()).isNull();
        assertThat(anonymousList.viewer()).isNull();

        assertHidden(crewService.detail(outsider.getId(), created.id()));
        assertHidden(find(crewService.list(outsider.getId(), PageRequest.of(0, 20)), created.id()));

        CrewDtos.ApplicationView pending = crewService.join(approved.getId(), created.id());
        assertThat(pending.status()).isEqualTo(CrewMemberStatus.PENDING);
        assertHidden(crewService.detail(approved.getId(), created.id()));
        assertHidden(find(crewService.list(approved.getId(), PageRequest.of(0, 20)), created.id()));
        assertHidden(crewService.myCrews(approved.getId(), PageRequest.of(0, 20)).items().getFirst().crew());

        crewService.review(
                owner.getId(),
                created.id(),
                pending.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.APPROVED));
        assertVisible(crewService.detail(approved.getId(), created.id()), openChatUrl);
        assertVisible(find(crewService.list(approved.getId(), PageRequest.of(0, 20)), created.id()), openChatUrl);
        assertVisible(crewService.myCrews(approved.getId(), PageRequest.of(0, 20)).items().getFirst().crew(), openChatUrl);

        CrewDtos.ApplicationView rejectedApplication = crewService.join(rejected.getId(), created.id());
        crewService.review(
                owner.getId(),
                created.id(),
                rejectedApplication.id(),
                new CrewDtos.ReviewRequest(CrewMemberStatus.REJECTED));
        CrewDtos.View rejectedView = crewService.detail(rejected.getId(), created.id());
        assertThat(rejectedView.viewer().membershipStatus()).isEqualTo(CrewMemberStatus.REJECTED);
        assertHidden(rejectedView);

        crewService.join(cancelled.getId(), created.id());
        crewService.cancelJoin(cancelled.getId(), created.id());
        CrewDtos.View cancelledView = crewService.detail(cancelled.getId(), created.id());
        assertThat(cancelledView.viewer().membershipStatus()).isEqualTo(CrewMemberStatus.CANCELLED);
        assertHidden(cancelledView);
    }

    @Test
    void ownerCanSetPreserveReplaceAndClearOpenChatUrl() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("chat-manage-" + suffix, "chat_manage_" + suffix);
        region(regions, "KR-SEOUL");
        String initial = "https://open.kakao.com/o/initial-" + suffix;

        CrewDtos.View created = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "managed chat crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                4,
                true,
                initial));
        assertVisible(created, initial);

        CrewDtos.View preserved = crewService.update(owner.getId(), created.id(), new CrewDtos.UpdateRequest(
                null, null, null, null, null, null, null));
        assertVisible(preserved, initial);

        String replacement = "https://open.kakao.com/o/replacement-" + suffix;
        CrewDtos.View replaced = crewService.update(owner.getId(), created.id(), new CrewDtos.UpdateRequest(
                null, null, null, null, null, null, replacement));
        assertVisible(replaced, replacement);

        CrewDtos.View cleared = crewService.update(owner.getId(), created.id(), new CrewDtos.UpdateRequest(
                null, null, null, null, null, null, "   "));
        assertThat(cleared.openChatUrl()).isNull();
        assertThat(cleared.viewer().canAccessOpenChat()).isFalse();
        assertThat(crewService.detail(owner.getId(), created.id()).openChatUrl()).isNull();
    }

    @Test
    void invalidOpenChatUrlsFailWithStableDomainErrorAndDoNotMutateStoredValue() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("chat-invalid-" + suffix, "chat_invalid_" + suffix);
        region(regions, "KR-SEOUL");
        String initial = "https://open.kakao.com/o/valid-" + suffix;
        CrewDtos.View created = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "validation chat crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                4,
                true,
                initial));

        List<String> invalidUrls = List.of(
                "http://open.kakao.com/o/insecure",
                "//open.kakao.com/o/scheme-relative",
                "https:///hostless",
                "https://user:secret@open.kakao.com/o/credential",
                "https://example.com/" + "a".repeat(500),
                "https://exa mple.com/o/bad");

        for (String invalid : invalidUrls) {
            assertThatThrownBy(() -> crewService.update(owner.getId(), created.id(), new CrewDtos.UpdateRequest(
                            null, null, null, null, null, null, invalid)))
                    .isInstanceOfSatisfying(DomainException.class, exception -> {
                        assertThat(exception.getStatus().value()).isEqualTo(400);
                        assertThat(exception.getCode()).isEqualTo("INVALID_CREW_OPEN_CHAT_URL");
                    });
            assertThat(crewService.detail(owner.getId(), created.id()).openChatUrl()).isEqualTo(initial);
        }
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }

    private CrewDtos.View find(PageResponse<CrewDtos.View> page, Long crewId) {
        return page.items().stream()
                .filter(view -> view.id().equals(crewId))
                .findFirst()
                .orElseThrow();
    }

    private void assertVisible(CrewDtos.View view, String expectedUrl) {
        assertThat(view.openChatUrl()).isEqualTo(expectedUrl);
        assertThat(view.viewer()).isNotNull();
        assertThat(view.viewer().canAccessOpenChat()).isTrue();
    }

    private void assertHidden(CrewDtos.View view) {
        assertThat(view.openChatUrl()).isNull();
        assertThat(view.viewer()).isNotNull();
        assertThat(view.viewer().canAccessOpenChat()).isFalse();
    }
}
