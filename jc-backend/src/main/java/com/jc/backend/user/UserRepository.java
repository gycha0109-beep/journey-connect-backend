package com.jc.backend.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    @Query("select count(u) > 0 from UserAccount u where lower(u.displayName) = lower(:nickname)")
    boolean existsByNickname(@Param("nickname") String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :userId")
    Optional<UserAccount> findByIdForUpdate(@Param("userId") Long userId);
}
