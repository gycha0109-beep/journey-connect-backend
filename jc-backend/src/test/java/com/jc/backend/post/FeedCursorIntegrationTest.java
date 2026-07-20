package com.jc.backend.post;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@CanonicalPostgresTest
@Transactional
class FeedCursorIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private PostService postService;
    @Autowired private EntityManager entityManager;

    @Test
    void cursorFeedReturnsEveryPostOnceWithoutOffsetCountQueryContract() {
        // 고정 이름 H2 DB나 선행 테스트 데이터에 의존하지 않도록 현재 테스트 데이터를 명시적으로 격리합니다.
        UserAccount author = users.save(new UserAccount("cursor@example.com", "hash", "cursor-user"));
        Region seoul = region(regions, "KR-SEOUL");
        for (int i = 1; i <= 5; i++) {
            posts.save(publishedPost(
                    places, author, seoul, "post-" + i, "content-" + i));
        }

        posts.flush();
        entityManager.clear();

        List<Long> expectedIds = posts.findByPublishedTrueOrderByCreatedAtDescIdDesc(
                        PageRequest.of(0, 10))
                .stream()
                .map(JourneyPost::getId)
                .toList();

        List<Long> collected = new ArrayList<>();
        String cursor = null;
        boolean hasNext;

        do {
            CursorPageResponse<PostDtos.Summary> page = postService.feed(cursor, 2);
            collected.addAll(page.items().stream()
                    .map(PostDtos.Summary::id)
                    .toList());
            cursor = page.nextCursor();
            hasNext = page.hasNext();
        } while (hasNext);

        assertThat(collected).containsExactlyElementsOf(expectedIds);
        assertThat(new HashSet<>(collected)).hasSameSizeAs(collected);
    }
}
