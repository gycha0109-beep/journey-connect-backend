package com.jc.backend.user;

import com.jc.backend.auth.AuthDtos;
import com.jc.backend.auth.AuthAccount;
import com.jc.backend.auth.AuthAccountRepository;
import com.jc.backend.auth.AuthService;
import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 사용자 프로필과 사용자별 게시물 조회 경계를 담당합니다. */
@Service
public class UserService {

    private final AuthAccountRepository authUsers;
    private final UserRepository publicUsers;
    private final JourneyPostRepository postRepository;
    private final UserLikedPostReadRepository likedPostReads;
    private final PostService posts;

    public UserService(
            AuthAccountRepository authUsers,
            UserRepository publicUsers,
            JourneyPostRepository postRepository,
            UserLikedPostReadRepository likedPostReads,
            PostService posts) {
        this.authUsers = authUsers;
        this.publicUsers = publicUsers;
        this.postRepository = postRepository;
        this.likedPostReads = likedPostReads;
        this.posts = posts;
    }

    @DatabaseTransactional(role = DatabaseRole.AUTH, readOnly = true)
    public AuthDtos.UserSummary me(long userId) {
        return AuthService.summary(authUser(userId));
    }

    @DatabaseTransactional(role = DatabaseRole.AUTH)
    public AuthDtos.UserSummary updateProfile(
            long userId,
            UserDtos.UpdateProfileRequest request) {
        AuthAccount user = authUser(userId);
        String nickname = normalizeNullableNickname(request.nickname());

        if (nickname != null
                && !nickname.equals(user.getNickname())
                && authUsers.existsByNickname(nickname)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "NICKNAME_ALREADY_USED",
                    "이미 사용 중인 닉네임입니다.");
        }

        user.updateProfile(nickname, request.bio(), request.profileImageUrl());
        return AuthService.summary(user);
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public UserDtos.PublicProfile publicProfile(long userId, Long viewerId) {
        UserAccount target = publicUser(userId);
        long publicPostCount = postRepository
                .findByAuthorIdAndPublishedTrueOrderByCreatedAtDescIdDesc(userId, Pageable.ofSize(1))
                .getTotalElements();
        return new UserDtos.PublicProfile(
                target.getId(),
                target.getNickname(),
                target.getBio(),
                target.getProfileImageUrl(),
                publicPostCount,
                viewerId == null
                        ? null
                        : new UserDtos.PublicProfileViewer(target.getId().equals(viewerId)));
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public PageResponse<PostDtos.Summary> publicPosts(long userId, Pageable pageable) {
        publicUser(userId);
        return posts.publicUserPosts(userId, pageable);
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public PageResponse<PostDtos.Summary> myPosts(long userId, Pageable pageable) {
        return posts.myPosts(userId, pageable);
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public PageResponse<PostDtos.Summary> myBookmarks(long userId, Pageable pageable) {
        return posts.myBookmarks(userId, pageable);
    }

    @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
    public PageResponse<PostDtos.Summary> myLikes(long userId, Pageable pageable) {
        activeAppUser(userId);
        Page<Long> likedPostIds = likedPostReads.findPublicPostIdsByUserId(userId, pageable);
        List<PostDtos.Summary> items = posts.summariesByOrderedIds(likedPostIds.getContent());
        return new PageResponse<>(
                items,
                likedPostIds.getNumber(),
                likedPostIds.getSize(),
                likedPostIds.getTotalElements(),
                likedPostIds.getTotalPages(),
                likedPostIds.isLast());
    }

    private UserAccount publicUser(long userId) {
        UserAccount user = publicUsers.findById(userId)
                .orElseThrow(this::userNotFound);
        if (!user.isActive()) {
            throw userNotFound();
        }
        return user;
    }

    private UserAccount activeAppUser(long userId) {
        UserAccount user = publicUsers.findById(userId)
                .orElseThrow(this::userNotFound);
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 해당 작업을 수행할 수 없습니다.");
        }
        return user;
    }

    private AuthAccount authUser(long userId) {
        AuthAccount user = authUsers.findById(userId)
                .orElseThrow(this::userNotFound);
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 프로필 작업을 수행할 수 없습니다.");
        }
        return user;
    }

    private DomainException userNotFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.");
    }

    private String normalizeNullableNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String normalized = nickname.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_NICKNAME",
                    "닉네임은 공백일 수 없습니다.");
        }
        return normalized;
    }
}
