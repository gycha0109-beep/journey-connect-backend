package com.jc.backend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    Optional<AuthAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from AuthAccount u where lower(u.displayName) = lower(:nickname)")
    boolean existsByNickname(@Param("nickname") String nickname);
}
