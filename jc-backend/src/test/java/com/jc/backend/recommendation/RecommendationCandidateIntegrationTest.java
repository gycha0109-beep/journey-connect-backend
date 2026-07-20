package com.jc.backend.recommendation;

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
import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class RecommendationCandidateIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private RecommendationCandidateSource source;
    @Autowired private RecommendationCoreInputMapper mapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void eligiblePublicPostMapsToRegisteredCoreFeatures() {
        UserAccount author = users.save(new UserAccount(
                "recommendation-author@example.com", "hash", "recommendation-author"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost post = posts.saveAndFlush(publishedPost(
                places, author, seoul, "recommendation candidate", "candidate content"));
        jdbcTemplate.update(
                """
                insert into public.post_tags (post_id, tag_id)
                select ?, t.id from public.tags t where t.slug in ('food', 'solo-travel')
                """,
                post.getId());

        List<RecommendationCandidateRow> rows = source.findEligible(author.getId(), 100);
        RecommendationCoreCandidate candidate = mapper.mapAll(rows).stream()
                .filter(value -> value.entity().id().equals(post.getId().toString()))
                .findFirst()
                .orElseThrow();

        assertThat(candidate.entity().entityType()).isEqualTo(RecommendationEntityType.POST);
        assertThat(candidate.entity().sourceId()).isEqualTo("journey-connect:posts");
        assertThat(candidate.entity().authorId()).isEqualTo(author.getId().toString());
        assertThat(candidate.features()).extracting(feature -> feature.featureId())
                .containsExactly("region:seoul", "companion:solo", "theme:food");
        assertThat(candidate.diversity().primaryRegionFeatureId()).isEqualTo("region:seoul");
        assertThat(candidate.diversity().primaryThemeFeatureId()).isEqualTo("theme:food");
        assertThat(candidate.exploration().duplicateGroupId())
                .isEqualTo("post:" + post.getId());
    }
}
