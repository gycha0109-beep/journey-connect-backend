package com.jc.backend.post;

import com.jc.backend.common.BaseTimeEntity;
import com.jc.backend.region.Region;
import com.jc.backend.user.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/** Canonical posts 행과 이미지·장소 결속을 관리합니다. */
@Entity
@Table(name = "posts")
public class JourneyPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_region_id")
    private Region region;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "view_count", nullable = false, insertable = false, updatable = false)
    private long viewCount;

    @Convert(converter = PostVisibilityConverter.class)
    @Column(nullable = false, length = 20)
    private PostVisibility visibility = PostVisibility.PUBLIC;

    @Convert(converter = PostStatusConverter.class)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.PUBLISHED;

    @Column(name = "published_at", insertable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    @Column(name = "purge_after", insertable = false, updatable = false)
    private Instant purgeAfter;

    @Convert(converter = PostModerationStatusConverter.class)
    @Column(name = "moderation_status", nullable = false, length = 20, insertable = false, updatable = false)
    private PostModerationStatus moderationStatus = PostModerationStatus.VISIBLE;

    @Column(name = "moderated_at", insertable = false, updatable = false)
    private Instant moderatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    @BatchSize(size = 100)
    private List<PostImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    private List<PostPlace> places = new ArrayList<>();

    protected JourneyPost() {}

    /** 기존 직접 생성 코드와 테스트 호환을 위해 기본값은 published/public입니다. */
    public JourneyPost(UserAccount author, Region region, String title, String content) {
        this(author, region, title, content, PostStatus.PUBLISHED);
    }

    public JourneyPost(
            UserAccount author,
            Region region,
            String title,
            String content,
            PostStatus initialStatus) {
        this.author = author;
        this.region = region;
        this.title = title;
        this.content = content;
        this.status = initialStatus;
        if (initialStatus == PostStatus.PUBLISHED) {
            this.publishedAt = Instant.now();
        }
    }

    public void update(String title, String content, Region region, Boolean published) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (content != null && !content.isBlank()) {
            this.content = content;
        }
        if (region != null) {
            this.region = region;
        }
        if (published != null) {
            this.status = published ? PostStatus.PUBLISHED : PostStatus.DRAFT;
            if (published && publishedAt == null) {
                publishedAt = Instant.now();
            }
        }
    }

    public void replaceImages(List<PostImageData> newImages) {
        images.clear();
        for (int index = 0; index < newImages.size(); index++) {
            PostImageData image = newImages.get(index);
            images.add(new PostImage(this, image.imageUrl(), index, image.altText()));
        }
    }

    public void replacePlaces(List<Place> newPlaces) {
        places.clear();
        for (int index = 0; index < newPlaces.size(); index++) {
            places.add(new PostPlace(this, newPlaces.get(index), index));
        }
    }

    public void delete() {
        status = PostStatus.DELETED;
    }

    public void applyViewCount(long value) {
        viewCount = value;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    public boolean isModerationVisible() {
        return moderationStatus == PostModerationStatus.VISIBLE;
    }

    public boolean isPublic() {
        return visibility == PostVisibility.PUBLIC;
    }

    public boolean hasPlaces() {
        return !places.isEmpty();
    }

    public Long getId() {
        return id;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public Region getRegion() {
        return region;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getRegionName() {
        return region == null ? null : region.getDisplayName();
    }

    public String getCoverImageUrl() {
        return images.isEmpty() ? null : images.get(0).getImageUrl();
    }

    public List<PostImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public List<PostPlace> getPlaces() {
        return Collections.unmodifiableList(places);
    }

    public long getViewCount() {
        return viewCount;
    }

    public PostStatus getStatus() {
        return status;
    }

    public PostVisibility getVisibility() {
        return visibility;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public record PostImageData(String imageUrl, String altText) {}
}
