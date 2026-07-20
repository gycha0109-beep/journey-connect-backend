package com.jc.backend.post;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.common.CursorCodec;
import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionDtos;
import com.jc.backend.region.RegionService;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 게시글 공개 정책과 canonical posts/places/interactions 트랜잭션 경계를 담당합니다. */
@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class PostService {

    private final JourneyPostRepository posts;
    private final PostLikeRepository likes;
    private final BookmarkRepository bookmarks;
    private final CommentRepository comments;
    private final PlaceRepository places;
    private final UserRepository users;
    private final PostInteractionWriter interactionWriter;
    private final PostViewCounter viewCounter;
    private final PostAccessPolicy accessPolicy;
    private final RegionService regionService;
    private final CursorCodec cursorCodec;

    public PostService(
            JourneyPostRepository posts,
            PostLikeRepository likes,
            BookmarkRepository bookmarks,
            CommentRepository comments,
            PlaceRepository places,
            UserRepository users,
            PostInteractionWriter interactionWriter,
            PostViewCounter viewCounter,
            PostAccessPolicy accessPolicy,
            RegionService regionService,
            CursorCodec cursorCodec) {
        this.posts = posts;
        this.likes = likes;
        this.bookmarks = bookmarks;
        this.comments = comments;
        this.places = places;
        this.users = users;
        this.interactionWriter = interactionWriter;
        this.viewCounter = viewCounter;
        this.accessPolicy = accessPolicy;
        this.regionService = regionService;
        this.cursorCodec = cursorCodec;
    }

    public CursorPageResponse<PostDtos.Summary> feed(String cursor, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        CursorCodec.CursorPosition position = cursorCodec.decode(cursor);
        PageRequest pageRequest = PageRequest.of(0, safeSize + 1);
        List<JourneyPost> fetched = position.publishedAt() == null
                ? posts.findFirstFeed(pageRequest)
                : posts.findFeedAfter(position.publishedAt(), position.id(), pageRequest);
        boolean hasNext = fetched.size() > safeSize;
        List<JourneyPost> pageItems = hasNext ? fetched.subList(0, safeSize) : fetched;
        List<PostDtos.Summary> summaries = summaries(pageItems);

        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            JourneyPost last = pageItems.get(pageItems.size() - 1);
            nextCursor = cursorCodec.encode(last.getPublishedAt(), last.getId());
        }
        return CursorPageResponse.of(summaries, nextCursor, hasNext);
    }

    public List<PostDtos.Summary> summariesByOrderedIds(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = orderedIds.stream().distinct().toList();
        if (distinct.size() != orderedIds.size()) {
            throw new IllegalArgumentException("ordered post IDs must be unique");
        }
        Map<Long, JourneyPost> visible = posts.findVisibleByIdIn(orderedIds).stream()
                .collect(Collectors.toMap(JourneyPost::getId, Function.identity()));
        List<JourneyPost> ordered = orderedIds.stream()
                .map(visible::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return summaries(ordered);
    }

    public PageResponse<PostDtos.Summary> feed(Pageable pageable) {
        return summaries(posts.findByPublishedTrueOrderByCreatedAtDescIdDesc(pageable));
    }

    public PageResponse<PostDtos.Summary> explore(String keyword, String region, Pageable pageable) {
        return summaries(posts.explore(blankToNull(keyword), normalizeRegionQuery(region), pageable));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public PostDtos.Detail detail(Long postId, Long viewerId) {
        JourneyPost post = readablePost(postId, viewerId);
        if (post.isPublished()) {
            post.applyViewCount(viewCounter.increment(postId, post.getViewCount()));
        }
        return detailView(post, viewerId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public PostDtos.Detail create(Long userId, PostDtos.CreateRequest request) {
        Region region = regionService.require(request.regionCode(), request.regionName());
        JourneyPost post = new JourneyPost(
                activeUser(userId),
                region,
                request.title().trim(),
                request.content(),
                PostStatus.DRAFT);
        post.replaceImages(imageData(request.images(), request.coverImageUrl()));
        post.replacePlaces(resolvePlaces(request.placeIds(), region));
        if (Boolean.TRUE.equals(request.published())) {
            post.update(null, null, null, true);
            ensurePublishable(post);
        }
        return detailView(posts.save(post), userId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public PostDtos.Detail update(Long userId, Long postId, PostDtos.UpdateRequest request) {
        JourneyPost post = ownedPost(userId, postId);
        Region region = hasText(request.regionCode()) || hasText(request.regionName())
                ? regionService.require(request.regionCode(), request.regionName())
                : null;

        if (request.placeIds() != null) {
            post.replacePlaces(resolvePlaces(
                    request.placeIds(), region == null ? post.getRegion() : region));
        }
        post.update(request.title(), request.content(), region, request.published());

        if (request.images() != null) {
            post.replaceImages(imageData(request.images(), null));
        } else if (request.coverImageUrl() != null) {
            post.replaceImages(imageData(null, request.coverImageUrl()));
        }
        if (post.isPublished()) {
            ensurePublishable(post);
        }
        return detailView(post, userId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void delete(Long userId, Long postId) {
        ownedPost(userId, postId).delete();
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public void like(Long userId, Long postId) {
        publishedPost(postId, userId);
        activeUser(userId);
        try {
            interactionWriter.addLike(postId, userId);
        } catch (DataIntegrityViolationException exception) {
            if (!likes.existsByPostIdAndUserId(postId, userId)) {
                throw exception;
            }
        }
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void unlike(Long userId, Long postId) {
        activeUser(userId);
        likes.deleteByPostIdAndUserId(postId, userId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public void bookmark(Long userId, Long postId) {
        publishedPost(postId, userId);
        activeUser(userId);
        try {
            interactionWriter.addBookmark(postId, userId);
        } catch (DataIntegrityViolationException exception) {
            if (!bookmarks.existsByPostIdAndUserId(postId, userId)) {
                throw exception;
            }
        }
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void unbookmark(Long userId, Long postId) {
        activeUser(userId);
        bookmarks.deleteByPostIdAndUserId(postId, userId);
    }

    public PageResponse<PostDtos.CommentView> comments(
            Long postId, Long viewerId, Pageable pageable) {
        readablePost(postId, viewerId);
        return PageResponse.from(comments.findVisibleByPostId(postId, pageable).map(this::commentView));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public PostDtos.CommentView addComment(Long userId, Long postId, String content) {
        JourneyPost post = publishedPost(postId, userId);
        Comment comment = comments.save(new Comment(post, activeUser(userId), content.trim()));
        return commentView(comment);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void deleteComment(Long userId, Long commentId) {
        activeUser(userId);
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> notFound("COMMENT_NOT_FOUND", "댓글"));
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "COMMENT_FORBIDDEN",
                    "본인 댓글만 삭제할 수 있습니다.");
        }
        comment.deleteByAuthor();
    }

    public PageResponse<PostDtos.Summary> publicUserPosts(Long userId, Pageable pageable) {
        return summaries(posts.findByAuthorIdAndPublishedTrueOrderByCreatedAtDescIdDesc(userId, pageable));
    }

    public PageResponse<PostDtos.Summary> myPosts(Long userId, Pageable pageable) {
        activeUser(userId);
        return summaries(posts.findByAuthorIdOrderByCreatedAtDescIdDesc(userId, pageable));
    }

    public PageResponse<PostDtos.Summary> myBookmarks(Long userId, Pageable pageable) {
        activeUser(userId);
        Page<JourneyPost> bookmarkedPosts =
                bookmarks.findVisibleByUserId(userId, pageable).map(Bookmark::getPost);
        return summaries(bookmarkedPosts);
    }

    private JourneyPost readablePost(Long postId, Long viewerId) {
        JourneyPost post = findPost(postId);
        if (post.isDeleted() || !post.isModerationVisible() || !post.getAuthor().isActive()) {
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        boolean owner = viewerId != null && post.getAuthor().getId().equals(viewerId);
        if (post.isDraft()) {
            if (owner) {
                return post;
            }
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        if (owner || accessPolicy.canView(viewerId, postId)) {
            return post;
        }
        throw notFound("POST_NOT_FOUND", "게시물");
    }

    private JourneyPost publishedPost(Long postId, Long viewerId) {
        JourneyPost post = readablePost(postId, viewerId);
        if (!post.isPublished()) {
            throw notFound("POST_NOT_FOUND", "게시물");
        }
        return post;
    }

    private JourneyPost ownedPost(Long userId, Long postId) {
        activeUser(userId);
        JourneyPost post = findPost(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "POST_FORBIDDEN",
                    "본인 게시물만 변경할 수 있습니다.");
        }
        if (!post.isModerationVisible()) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "POST_MODERATION_LOCKED",
                    "관리자에 의해 숨김 처리된 게시물은 변경할 수 없습니다.");
        }
        return post;
    }

    private JourneyPost findPost(Long postId) {
        return posts.findWithDetailById(postId)
                .orElseThrow(() -> notFound("POST_NOT_FOUND", "게시물"));
    }

    private UserAccount activeUser(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "사용자"));
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 해당 작업을 수행할 수 없습니다.");
        }
        return user;
    }

    private List<Place> resolvePlaces(List<Long> placeIds, Region mainRegion) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Long, Boolean> uniqueIds = new LinkedHashMap<>();
        for (Long placeId : placeIds) {
            if (placeId == null || placeId <= 0 || uniqueIds.put(placeId, Boolean.TRUE) != null) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PLACE_IDS",
                        "장소 ID는 양수이며 중복될 수 없습니다.");
            }
        }
        Map<Long, Place> byId = places.findAllById(uniqueIds.keySet()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (byId.size() != uniqueIds.size()) {
            throw notFound("PLACE_NOT_FOUND", "장소");
        }
        return uniqueIds.keySet().stream().map(byId::get).peek(place -> {
            if (!place.isActive()) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "PLACE_INACTIVE",
                        "비활성 장소는 게시물에 연결할 수 없습니다.");
            }
            if (!isWithin(mainRegion, place.getRegion())) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "PLACE_OUTSIDE_REGION",
                        "게시물 장소가 대표 지역 계층 밖에 있습니다.");
            }
        }).toList();
    }

    private boolean isWithin(Region ancestor, Region candidate) {
        Region current = candidate;
        while (current != null) {
            if (current.getId() != null && current.getId().equals(ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void ensurePublishable(JourneyPost post) {
        if (post.getRegion() == null || !post.hasPlaces()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "POST_PLACE_REQUIRED",
                    "게시물을 발행하려면 대표 지역과 한 개 이상의 장소가 필요합니다.");
        }
    }

    private DomainException notFound(String code, String target) {
        return new DomainException(HttpStatus.NOT_FOUND, code, target + "을(를) 찾을 수 없습니다.");
    }

    private PageResponse<PostDtos.Summary> summaries(Page<JourneyPost> page) {
        List<PostDtos.Summary> items = summaries(page.getContent());
        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    private List<PostDtos.Summary> summaries(List<JourneyPost> postsPage) {
        List<Long> postIds = postsPage.stream().map(JourneyPost::getId).toList();
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> likeCounts = countMap(likes.countByPostIds(postIds));
        Map<Long, Long> bookmarkCounts = countMap(bookmarks.countByPostIds(postIds));
        return postsPage.stream().map(post -> summary(post, likeCounts, bookmarkCounts)).toList();
    }

    private Map<Long, Long> countMap(List<PostCountProjection> counts) {
        if (counts.isEmpty()) {
            return Collections.emptyMap();
        }
        return counts.stream().collect(Collectors.toUnmodifiableMap(
                PostCountProjection::getPostId,
                PostCountProjection::getTotal,
                (existing, ignored) -> existing));
    }

    private PostDtos.Summary summary(
            JourneyPost post,
            Map<Long, Long> likeCounts,
            Map<Long, Long> bookmarkCounts) {
        return new PostDtos.Summary(
                post.getId(),
                post.getTitle(),
                post.getRegion().getCode(),
                post.getRegionName(),
                post.getCoverImageUrl(),
                post.getViewCount(),
                likeCounts.getOrDefault(post.getId(), 0L),
                bookmarkCounts.getOrDefault(post.getId(), 0L),
                author(post.getAuthor()),
                post.getCreatedAt());
    }

    private PostDtos.Detail detailView(JourneyPost post, Long viewerId) {
        boolean liked = viewerId != null && likes.existsByPostIdAndUserId(post.getId(), viewerId);
        boolean bookmarked = viewerId != null
                && bookmarks.existsByPostIdAndUserId(post.getId(), viewerId);
        return new PostDtos.Detail(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                regionView(post.getRegion()),
                post.getRegionName(),
                post.getCoverImageUrl(),
                post.getImages().stream().map(this::imageView).toList(),
                post.getViewCount(),
                likes.countByPostId(post.getId()),
                bookmarks.countByPostId(post.getId()),
                liked,
                bookmarked,
                author(post.getAuthor()),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private PostDtos.ImageView imageView(PostImage image) {
        return new PostDtos.ImageView(
                image.getId(), image.getImageUrl(), image.getSortOrder(), image.getAltText());
    }

    private RegionDtos.View regionView(Region region) {
        return new RegionDtos.View(
                region.getId(),
                region.getCode(),
                region.getCountryCode(),
                region.getDisplayName(),
                region.getCenterLatitude(),
                region.getCenterLongitude());
    }

    private PostDtos.CommentView commentView(Comment comment) {
        return new PostDtos.CommentView(
                comment.getId(), comment.getContent(), author(comment.getAuthor()), comment.getCreatedAt());
    }

    private PostDtos.Author author(UserAccount user) {
        return new PostDtos.Author(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }

    private List<JourneyPost.PostImageData> imageData(
            List<PostDtos.ImageRequest> images, String legacyCoverImageUrl) {
        if (images != null) {
            return images.stream()
                    .map(image -> new JourneyPost.PostImageData(
                            image.imageUrl().trim(), blankToNull(image.altText())))
                    .toList();
        }
        if (hasText(legacyCoverImageUrl)) {
            return List.of(new JourneyPost.PostImageData(legacyCoverImageUrl.trim(), null));
        }
        return List.of();
    }

    private String normalizeRegionQuery(String region) {
        String value = blankToNull(region);
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
