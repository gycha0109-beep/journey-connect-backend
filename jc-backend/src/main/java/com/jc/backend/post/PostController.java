package com.jc.backend.post;

import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.common.PageResponse;
import com.jc.backend.recommendation.application.RecommendationFeedService;
import com.jc.backend.recommendation.application.RecommendationPostInteractionService;
import com.jc.backend.recommendation.application.RecommendationPostInteractionService.TrackingContext;
import com.jc.backend.recommendation.persistence.RecommendationPostInteractionStore.Action;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;
    private final RecommendationFeedService recommendationFeedService;
    private final RecommendationPostInteractionService recommendationPostInteractionService;
    private final ExploreSearchShadowBridge exploreSearchShadowBridge;

    public PostController(
            PostService postService,
            RecommendationFeedService recommendationFeedService,
            RecommendationPostInteractionService recommendationPostInteractionService,
            ExploreSearchShadowBridge exploreSearchShadowBridge) {
        this.postService = postService;
        this.recommendationFeedService = recommendationFeedService;
        this.recommendationPostInteractionService = recommendationPostInteractionService;
        this.exploreSearchShadowBridge = exploreSearchShadowBridge;
    }

    @GetMapping("/feed")
    ApiResponse<CursorPageResponse<PostDtos.Summary>> feed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(recommendationFeedService.feed(
                cursor,
                size,
                userIdOrNull(token),
                token == null ? null : token.getId()));
    }

    /** 기존 페이지 번호 기반 소비자를 위한 호환 경로입니다. 신규 화면은 /feed를 사용합니다. */
    @GetMapping("/feed/page")
    ApiResponse<PageResponse<PostDtos.Summary>> feedPage(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(postService.feed(pageable));
    }

    @GetMapping("/explore")
    ApiResponse<PageResponse<PostDtos.Summary>> explore(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);
        exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);
        return ApiResponse.ok(legacyResponse);
    }

    @GetMapping("/posts/{postId}")
    ApiResponse<PostDtos.Detail> detail(
            @PathVariable Long postId,
            @AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(postService.detail(postId, userIdOrNull(token)));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PostDtos.Detail> create(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody PostDtos.CreateRequest request) {
        return ApiResponse.created(postService.create(userId(token), request));
    }

    @PatchMapping("/posts/{postId}")
    ApiResponse<PostDtos.Detail> update(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @Valid @RequestBody PostDtos.UpdateRequest request) {
        return ApiResponse.ok(postService.update(userId(token), postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt token, @PathVariable Long postId) {
        postService.delete(userId(token), postId);
    }

    @PostMapping("/posts/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void like(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @RequestHeader(name = "X-Recommendation-Run-Id", required = false) String runId,
            @RequestHeader(name = "X-Recommendation-Event-Id", required = false) String eventId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Recommendation-Occurred-At", required = false) Instant occurredAt) {
        recommendationPostInteractionService.apply(
                userId(token),
                token.getId(),
                postId,
                Action.LIKE,
                new TrackingContext(runId, eventId, idempotencyKey, occurredAt));
    }

    @DeleteMapping("/posts/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlike(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @RequestHeader(name = "X-Recommendation-Run-Id", required = false) String runId,
            @RequestHeader(name = "X-Recommendation-Event-Id", required = false) String eventId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Recommendation-Occurred-At", required = false) Instant occurredAt) {
        recommendationPostInteractionService.apply(
                userId(token),
                token.getId(),
                postId,
                Action.UNLIKE,
                new TrackingContext(runId, eventId, idempotencyKey, occurredAt));
    }

    @PostMapping("/posts/{postId}/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void bookmark(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @RequestHeader(name = "X-Recommendation-Run-Id", required = false) String runId,
            @RequestHeader(name = "X-Recommendation-Event-Id", required = false) String eventId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Recommendation-Occurred-At", required = false) Instant occurredAt) {
        recommendationPostInteractionService.apply(
                userId(token),
                token.getId(),
                postId,
                Action.SAVE,
                new TrackingContext(runId, eventId, idempotencyKey, occurredAt));
    }

    @DeleteMapping("/posts/{postId}/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unbookmark(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @RequestHeader(name = "X-Recommendation-Run-Id", required = false) String runId,
            @RequestHeader(name = "X-Recommendation-Event-Id", required = false) String eventId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Recommendation-Occurred-At", required = false) Instant occurredAt) {
        recommendationPostInteractionService.apply(
                userId(token),
                token.getId(),
                postId,
                Action.UNSAVE,
                new TrackingContext(runId, eventId, idempotencyKey, occurredAt));
    }

    @GetMapping("/posts/{postId}/comments")
    ApiResponse<PageResponse<PostDtos.CommentView>> comments(
            @PathVariable Long postId,
            @AuthenticationPrincipal Jwt token,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(postService.comments(postId, userIdOrNull(token), pageable));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PostDtos.CommentView> comment(
            @AuthenticationPrincipal Jwt token,
            @PathVariable Long postId,
            @Valid @RequestBody PostDtos.CommentRequest request) {
        return ApiResponse.created(
                postService.addComment(userId(token), postId, request.content()));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteComment(@AuthenticationPrincipal Jwt token, @PathVariable Long commentId) {
        postService.deleteComment(userId(token), commentId);
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    private Long userIdOrNull(Jwt token) {
        return token == null ? null : userId(token);
    }
}
