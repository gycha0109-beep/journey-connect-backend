package com.jc.backend.intelligence.search;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationSearchCandidateSourceIntegrationTest {

    @Autowired private RecommendationSearchCandidateSource candidateSource;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount viewer;
    private UserAccount activeAuthor;
    private Region luxembourg;
    private Region seoul;

    @BeforeEach
    void setUp() {
        viewer = users.save(new UserAccount(
                "search-viewer@example.com", "hash", "search-viewer"));
        activeAuthor = users.save(new UserAccount(
                "search-author@example.com", "hash", "search-author"));
        seoul = region(regions, "KR-SEOUL");
        luxembourg = createLuxembourg();
    }

    @Test
    void luxembourgRegionReturnsOnlyLuxembourgCandidates() {
        JourneyPost lux = save(activeAuthor, luxembourg, "카페 산책", "도심 여행");
        save(activeAuthor, seoul, "카페 산책", "서울 여행");

        List<RecommendationSearchCandidateRow> result = find("카페", "룩셈부르크");

        assertThat(result).extracting(RecommendationSearchCandidateRow::postId)
                .containsExactly(lux.getId());
        assertThat(result).allMatch(candidate ->
                candidate.regionNames().contains("룩셈부르크"));
    }

    @Test
    void keywordMismatchIsExcludedInsideSelectedRegion() {
        JourneyPost matching = save(activeAuthor, luxembourg, "카페 산책", "도심 여행");
        save(activeAuthor, luxembourg, "박물관 산책", "역사 여행");

        List<RecommendationSearchCandidateRow> result = find("카페", "룩셈부르크");

        assertThat(result).extracting(RecommendationSearchCandidateRow::postId)
                .containsExactly(matching.getId());
    }

    @Test
    void activeTagCanSatisfyKeywordWithoutTitleOrContentMatch() {
        JourneyPost tagged = save(activeAuthor, luxembourg, "도심 산책", "조용한 여행");
        attachTag(tagged.getId(), "cafe");

        List<RecommendationSearchCandidateRow> result = find("카페", "룩셈부르크");

        assertThat(result).extracting(RecommendationSearchCandidateRow::postId)
                .containsExactly(tagged.getId());
        assertThat(result.get(0).tagExactMatch()).isTrue();
        assertThat(result.get(0).tagSlugs()).contains("cafe");
    }

    @Test
    void draftAndModerationHiddenPostsAreExcluded() {
        JourneyPost visible = save(activeAuthor, luxembourg, "카페 공개", "여행");
        JourneyPost draft = save(activeAuthor, luxembourg, "카페 초안", "여행");
        draft.update(null, null, null, false);
        posts.save(draft);
        JourneyPost hidden = save(activeAuthor, luxembourg, "카페 숨김", "여행");
        jdbcTemplate.update(
                "update public.posts set moderation_status = 'hidden' where id = ?",
                hidden.getId());

        List<RecommendationSearchCandidateRow> result = find("카페", "룩셈부르크");

        assertThat(result).extracting(RecommendationSearchCandidateRow::postId)
                .containsExactly(visible.getId());
    }

    @Test
    void inactiveAuthorPostsAreExcluded() {
        UserAccount inactive = users.save(new UserAccount(
                "search-inactive@example.com", "hash", "search-inactive"));
        JourneyPost visible = save(activeAuthor, luxembourg, "카페 공개", "여행");
        JourneyPost excluded = save(inactive, luxembourg, "카페 비활성", "여행");
        jdbcTemplate.update(
                "update public.app_users set account_status = 'suspended' where id = ?",
                inactive.getId());

        List<RecommendationSearchCandidateRow> result = find("카페", "룩셈부르크");

        assertThat(result).extracting(RecommendationSearchCandidateRow::postId)
                .containsExactly(visible.getId())
                .doesNotContain(excluded.getId());
    }

    private List<RecommendationSearchCandidateRow> find(String keyword, String region) {
        return candidateSource.findEligible(
                viewer.getId(),
                keyword,
                region,
                100,
                Instant.parse("2099-01-01T00:00:00Z"));
    }

    private JourneyPost save(
            UserAccount author,
            Region region,
            String title,
            String content) {
        return posts.save(publishedPost(places, author, region, title, content));
    }

    private void attachTag(long postId, String slug) {
        Long tagId = jdbcTemplate.queryForObject(
                "select id from public.tags where slug = ?",
                Long.class,
                slug);
        jdbcTemplate.update(
                "insert into public.post_tags (post_id, tag_id) values (?, ?)",
                postId,
                tagId);
    }

    private Region createLuxembourg() {
        Long countryId = jdbcTemplate.queryForObject(
                """
                insert into public.regions (
                  name_local, name_ko, name_en, slug, region_type, country_code, timezone
                ) values (
                  'Lëtzebuerg', '룩셈부르크', 'Luxembourg', 'lu', 'country', 'LU',
                  'Europe/Luxembourg'
                )
                returning id
                """,
                Long.class);
        jdbcTemplate.update(
                """
                insert into public.regions (
                  parent_id, name_local, name_ko, name_en, slug, region_type,
                  country_code, timezone
                ) values (?, 'Luxembourg', '룩셈부르크', 'Luxembourg', 'lu-lux',
                  'city', 'LU', 'Europe/Luxembourg')
                """,
                countryId);
        return regions.findByCodeIgnoreCase("LU-LUX")
                .orElseThrow(() -> new AssertionError("Luxembourg region missing"));
    }
}
