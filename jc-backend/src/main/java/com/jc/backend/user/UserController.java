package com.jc.backend.user;

import com.jc.backend.auth.AuthDtos;
import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserFollowService userFollowService;

    public UserController(UserService userService, UserFollowService userFollowService) {
        this.userService = userService;
        this.userFollowService = userFollowService;
    }

    @GetMapping("/me")
    ApiResponse<AuthDtos.UserSummary> me(@AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(userService.me(userId(token)));
    }

    @PatchMapping("/me")
    ApiResponse<AuthDtos.UserSummary> update(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(userId(token), request));
    }

    @GetMapping("/{userId}")
    ApiResponse<UserDtos.PublicProfile> publicProfile(
            @PathVariable long userId,
            @AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(userService.publicProfile(userId, userIdOrNull(token)));
    }

    @GetMapping("/{userId}/posts")
    ApiResponse<PageResponse<PostDtos.Summary>> userPosts(
            @PathVariable long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(userService.publicPosts(userId, pageable));
    }

    @PostMapping("/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void follow(
            @AuthenticationPrincipal Jwt token,
            @PathVariable long userId) {
        userFollowService.follow(userId(token), userId);
    }

    @DeleteMapping("/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfollow(
            @AuthenticationPrincipal Jwt token,
            @PathVariable long userId) {
        userFollowService.unfollow(userId(token), userId);
    }

    @GetMapping("/me/posts")
    ApiResponse<PageResponse<PostDtos.Summary>> myPosts(
            @AuthenticationPrincipal Jwt token,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(userService.myPosts(userId(token), pageable));
    }

    @GetMapping("/me/bookmarks")
    ApiResponse<PageResponse<PostDtos.Summary>> bookmarks(
            @AuthenticationPrincipal Jwt token,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(userService.myBookmarks(userId(token), pageable));
    }

    @GetMapping("/me/likes")
    ApiResponse<PageResponse<PostDtos.Summary>> likes(
            @AuthenticationPrincipal Jwt token,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(userService.myLikes(userId(token), pageable));
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }

    private Long userIdOrNull(Jwt token) {
        return token == null ? null : userId(token);
    }
}
