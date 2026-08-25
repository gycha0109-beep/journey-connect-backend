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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewTagIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private CrewService crewService;
    @Autowired private CrewTagService crewTags;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void ownerCanReplaceCanonicalTagsAndPublicReadPreservesRequestedOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("tag-owner-" + suffix, "tag_owner_" + suffix);
        region(regions, "KR-SEOUL");
        tag("photo-walk", "포토워크", 20);
        tag("night", "야경", 10);

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "tagged crew",
                "KR-SEOUL",
                null,
                "description",
                LocalDate.now().plusDays(10),
                5,
                true));

        List<CrewTagDtos.TagView> replaced = crewTags.replace(
                owner.getId(),
                crew.id(),
                new CrewTagDtos.ReplaceRequest(List.of("PHOTO_WALK", "night")));

        assertThat(replaced).extracting(CrewTagDtos.TagView::slug)
                .containsExactly("photo-walk", "night");
        assertThat(crewTags.list(crew.id())).extracting(CrewTagDtos.TagView::slug)
                .containsExactly("night", "photo-walk");
        assertThat(jdbc.queryForObject(
                "select count(*) from public.crew_tags where crew_id = ?",
                Long.class,
                crew.id())).isEqualTo(2L);
    }

    @Test
    void nonOwnerUnknownDuplicateAndExcessTagsFailClosed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("tag-owner2-" + suffix, "tag_owner2_" + suffix);
        UserAccount outsider = user("tag-outsider-" + suffix, "tag_out_" + suffix);
        region(regions, "KR-BUSAN");
        tag("food", "맛집", 0);

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "guarded crew",
                "KR-BUSAN",
                null,
                "description",
                LocalDate.now().plusDays(10),
                5,
                true));

        assertThatThrownBy(() -> crewTags.replace(
                outsider.getId(),
                crew.id(),
                new CrewTagDtos.ReplaceRequest(List.of("food"))))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_OWNER_REQUIRED"));

        assertThatThrownBy(() -> crewTags.replace(
                owner.getId(),
                crew.id(),
                new CrewTagDtos.ReplaceRequest(List.of("missing-tag"))))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_TAG_NOT_FOUND"));

        assertThatThrownBy(() -> crewTags.replace(
                owner.getId(),
                crew.id(),
                new CrewTagDtos.ReplaceRequest(List.of("food", "FOOD"))))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_TAG_DUPLICATE"));

        assertThatThrownBy(() -> crewTags.replace(
                owner.getId(),
                crew.id(),
                new CrewTagDtos.ReplaceRequest(List.of("a", "b", "c", "d", "e", "f"))))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CREW_TAG_LIMIT_EXCEEDED"));
    }

    @Test
    void clearingTagsProducesLegacyTaglessCoverage() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = user("tag-owner3-" + suffix, "tag_owner3_" + suffix);
        region(regions, "KR-JEJU");
        tag("nature", "자연", 0);

        CrewDtos.View crew = crewService.create(owner.getId(), new CrewDtos.CreateRequest(
                "clearable crew",
                "KR-JEJU",
                null,
                "description",
                LocalDate.now().plusDays(10),
                5,
                true));
        crewTags.replace(owner.getId(), crew.id(), new CrewTagDtos.ReplaceRequest(List.of("nature")));
        crewTags.replace(owner.getId(), crew.id(), new CrewTagDtos.ReplaceRequest(List.of()));

        assertThat(crewTags.list(crew.id())).isEmpty();
    }

    private void tag(String slug, String nameKo, int sortOrder) {
        jdbc.update(
                "insert into public.tags (slug, name_ko, sort_order) values (?, ?, ?) "
                        + "on conflict (slug) do update set name_ko = excluded.name_ko, sort_order = excluded.sort_order, is_active = true",
                slug,
                nameKo,
                sortOrder);
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.save(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
