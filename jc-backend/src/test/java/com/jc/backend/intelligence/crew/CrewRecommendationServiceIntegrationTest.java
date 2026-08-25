package com.jc.backend.intelligence.crew;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.crew.CrewDtos;
import com.jc.backend.crew.CrewService;
import com.jc.backend.crew.CrewTagDtos;
import com.jc.backend.crew.CrewTagService;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class CrewRecommendationServiceIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private CrewService crewService;
    @Autowired private CrewTagService crewTags;
    @Autowired private CrewRecommendationService recommendations;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void explicitP1SignalsRankMatchingCrewAheadWithoutChangingLegacyCrewList() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount viewer = user("crew-rec-viewer-" + suffix, "cr_view_" + suffix);
        UserAccount seoulOwner = user("crew-rec-seoul-" + suffix, "cr_seoul_" + suffix);
        UserAccount busanOwner = user("crew-rec-busan-" + suffix, "cr_busan_" + suffix);

        CrewDtos.View seoul = crewService.create(seoulOwner.getId(), new CrewDtos.CreateRequest(
                "Seoul food crew",
                "KR-SEOUL",
                null,
                "food focused",
                LocalDate.now().plusDays(14),
                5,
                true));
        CrewDtos.View busan = crewService.create(busanOwner.getId(), new CrewDtos.CreateRequest(
                "Busan nature crew",
                "KR-BUSAN",
                null,
                "nature focused",
                LocalDate.now().plusDays(14),
                5,
                true));

        crewTags.replace(seoulOwner.getId(), seoul.id(), new CrewTagDtos.ReplaceRequest(List.of("food")));
        crewTags.replace(busanOwner.getId(), busan.id(), new CrewTagDtos.ReplaceRequest(List.of("nature")));

        preference(viewer.getId(), "region:seoul", "prefer", 1.0d);
        preference(viewer.getId(), "theme:food", "prefer", 1.0d);

        Instant referenceTime = Instant.now().plusSeconds(1);
        CrewRecommendationService.RecommendationResult result =
                recommendations.recommend(viewer.getId(), 10, referenceTime);

        assertThat(result.contractVersion()).isEqualTo("crew-recommendation-contract-v1");
        assertThat(result.rankingPolicyVersion()).isEqualTo("crew-ranking-policy-v1");
        assertThat(result.scorePolicyVersion()).isEqualTo("crew-score-policy-v1");
        assertThat(result.crews()).extracting(item -> item.facts().crewId())
                .containsSubsequence(seoul.id(), busan.id());

        CrewRecommendationRanker.RankedCrew seoulRanked = result.crews().stream()
                .filter(item -> item.facts().crewId() == seoul.id())
                .findFirst()
                .orElseThrow();
        CrewRecommendationRanker.RankedCrew busanRanked = result.crews().stream()
                .filter(item -> item.facts().crewId() == busan.id())
                .findFirst()
                .orElseThrow();
        assertThat(seoulRanked.breakdown().tagInterest()).isPositive();
        assertThat(seoulRanked.breakdown().regionInterest()).isPositive();
        assertThat(seoulRanked.breakdown().totalScore())
                .isGreaterThan(busanRanked.breakdown().totalScore());

        assertThat(crewService.list(null, null, null, org.springframework.data.domain.PageRequest.of(0, 20))
                .items())
                .extracting(CrewDtos.View::id)
                .contains(seoul.id(), busan.id());
    }

    private void preference(long userId, String featureId, String kind, double strength) {
        jdbc.update(
                """
                insert into public.recommendation_user_preference
                    (user_id, feature_id, preference_kind, strength, active)
                values (?, ?, ?, ?, true)
                on conflict (user_id, feature_id) do update
                set preference_kind = excluded.preference_kind,
                    strength = excluded.strength,
                    active = true,
                    updated_at = current_timestamp
                """,
                userId,
                featureId,
                kind,
                strength);
    }

    private UserAccount user(String emailPrefix, String nickname) {
        return users.saveAndFlush(new UserAccount(emailPrefix + "@example.com", "hash", nickname));
    }
}
