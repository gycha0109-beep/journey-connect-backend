package com.jc.backend.post;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
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

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostInteractionIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private PostLikeRepository likes;
    @Autowired private BookmarkRepository bookmarks;
    @Autowired private PostService postService;

    @Test
    void duplicateAndConcurrentLikeBookmarkRequestsRemainIdempotent() throws Exception {
        UserAccount author = users.save(new UserAccount("author2@example.com", "hash", "author2"));
        UserAccount user = users.save(new UserAccount("user2@example.com", "hash", "user2"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost post = posts.save(publishedPost(places, author, seoul, "post", "content"));

        runConcurrently(4, () -> postService.like(user.getId(), post.getId()));
        runConcurrently(4, () -> postService.bookmark(user.getId(), post.getId()));

        postService.like(user.getId(), post.getId());
        postService.bookmark(user.getId(), post.getId());

        assertThat(likes.countByPostId(post.getId())).isEqualTo(1);
        assertThat(bookmarks.countByPostId(post.getId())).isEqualTo(1);
    }

    private void runConcurrently(int workers, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    action.run();
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
