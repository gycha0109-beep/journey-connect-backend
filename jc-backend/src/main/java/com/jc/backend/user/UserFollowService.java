package com.jc.backend.user;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 인증 사용자의 canonical follow edge mutation을 담당합니다. */
@Service
public class UserFollowService {

    private final UserRepository users;
    private final UserFollowRepository follows;

    public UserFollowService(UserRepository users, UserFollowRepository follows) {
        this.users = users;
        this.follows = follows;
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void follow(long actorUserId, long targetUserId) {
        requireActiveActor(actorUserId);
        rejectSelf(actorUserId, targetUserId);
        requireActiveTarget(targetUserId);
        follows.follow(actorUserId, targetUserId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void unfollow(long actorUserId, long targetUserId) {
        requireActiveActor(actorUserId);
        rejectSelf(actorUserId, targetUserId);
        requireActiveTarget(targetUserId);
        follows.unfollow(actorUserId, targetUserId);
    }

    private void requireActiveActor(long userId) {
        UserAccount actor = users.findById(userId).orElseThrow(this::userNotFound);
        if (!actor.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 해당 작업을 수행할 수 없습니다.");
        }
    }

    private void requireActiveTarget(long userId) {
        UserAccount target = users.findById(userId).orElseThrow(this::userNotFound);
        if (!target.isActive()) {
            throw userNotFound();
        }
    }

    private void rejectSelf(long actorUserId, long targetUserId) {
        if (actorUserId == targetUserId) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "FOLLOW_SELF_NOT_ALLOWED",
                    "자기 자신을 팔로우 대상으로 지정할 수 없습니다.");
        }
    }

    private DomainException userNotFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다.");
    }
}
