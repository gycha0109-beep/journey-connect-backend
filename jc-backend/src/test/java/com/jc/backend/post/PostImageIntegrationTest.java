package com.jc.backend.post;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class PostImageIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private PostService postService;

    @Test
    void postStoresOrderedImagesAndUsesFirstImageAsCover() {
        UserAccount author = users.save(new UserAccount("images@example.com", "hash", "image-user"));
        region(regions, "KR-SEOUL");

        PostDtos.Detail created = postService.create(author.getId(), new PostDtos.CreateRequest(
                "multi image",
                "content",
                "KR-SEOUL",
                null,
                null,
                List.of(
                        new PostDtos.ImageRequest("https://example.com/1.jpg", "first"),
                        new PostDtos.ImageRequest("https://example.com/2.jpg", "second"))));

        assertThat(created.coverImageUrl()).isEqualTo("https://example.com/1.jpg");
        assertThat(created.images())
                .extracting(PostDtos.ImageView::imageUrl)
                .containsExactly(
                        "https://example.com/1.jpg",
                        "https://example.com/2.jpg");

        PostDtos.Detail updated = postService.update(author.getId(), created.id(), new PostDtos.UpdateRequest(
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null));

        assertThat(updated.images()).isEmpty();
        assertThat(updated.coverImageUrl()).isNull();
    }
}
