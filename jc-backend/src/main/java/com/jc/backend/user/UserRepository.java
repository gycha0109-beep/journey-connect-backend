package com.jc.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    @Query("select count(u) > 0 from UserAccount u where lower(u.displayName) = lower(:nickname)")
    boolean existsByNickname(@Param("nickname") String nickname);
}
