package com.jc.backend.user;

import com.jc.backend.auth.AuthDtos;
import com.jc.backend.auth.AuthAccount;
import com.jc.backend.auth.AuthAccountRepository;
import com.jc.backend.auth.AuthService;
import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 사용자 프로필과 사용자별 게시물 조회 경계를 담당합니다. */
@Service
public class UserService {

    private final AuthAccountRepository authUsers;
    private final PostService posts;

    public UserService(AuthAccountRepository authUsers, PostService posts) {
        this.authUsers = authUsers;
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
    public PageResponse<PostDtos.Summary> publicPosts(long userId, Pageable pageable) {
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

    private AuthAccount authUser(long userId) {
        AuthAccount user = authUsers.findById(userId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 프로필 작업을 수행할 수 없습니다.");
        }
        return user;
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
