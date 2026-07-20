package com.jc.backend.post;

import com.jc.backend.region.RegionDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class PostDtos {

    private PostDtos() {}

    public record ImageRequest(
            @NotBlank @Size(max = 2000) String imageUrl,
            @Size(max = 300) String altText) {}

    public record CreateRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank String content,
            @Size(max = 160) String regionCode,
            @Size(max = 100) String regionName,
            @Size(max = 2000) String coverImageUrl,
            @Size(max = 10) List<@Valid ImageRequest> images,
            @Size(max = 20) List<Long> placeIds,
            Boolean published) {

        /** 기존 6개 필드 호출은 draft 생성으로 유지합니다. */
        public CreateRequest(
                String title,
                String content,
                String regionCode,
                String regionName,
                String coverImageUrl,
                List<ImageRequest> images) {
            this(title, content, regionCode, regionName, coverImageUrl, images, null, false);
        }
    }

    public record UpdateRequest(
            @Size(max = 150) String title,
            String content,
            @Size(max = 160) String regionCode,
            @Size(max = 100) String regionName,
            @Size(max = 2000) String coverImageUrl,
            @Size(max = 10) List<@Valid ImageRequest> images,
            @Size(max = 20) List<Long> placeIds,
            Boolean published) {

        public UpdateRequest(
                String title,
                String content,
                String regionCode,
                String regionName,
                String coverImageUrl,
                List<ImageRequest> images,
                Boolean published) {
            this(title, content, regionCode, regionName, coverImageUrl, images, null, published);
        }
    }

    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {}

    public record Author(Long id, String nickname, String profileImageUrl) {}

    public record ImageView(Long id, String imageUrl, int sortOrder, String altText) {}

    public record Summary(
            Long id,
            String title,
            String regionCode,
            String regionName,
            String coverImageUrl,
            long viewCount,
            long likeCount,
            long bookmarkCount,
            Author author,
            Instant createdAt) {}

    public record Detail(
            Long id,
            String title,
            String content,
            RegionDtos.View region,
            String regionName,
            String coverImageUrl,
            List<ImageView> images,
            long viewCount,
            long likeCount,
            long bookmarkCount,
            boolean liked,
            boolean bookmarked,
            Author author,
            Instant createdAt,
            Instant updatedAt) {}

    public record CommentView(Long id, String content, Author author, Instant createdAt) {}
}
