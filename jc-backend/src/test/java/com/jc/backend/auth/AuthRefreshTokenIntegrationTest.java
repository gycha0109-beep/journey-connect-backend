package com.jc.backend.auth;

import com.jc.backend.CanonicalPostgresTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRefreshTokenIntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    void refreshTokenIsRotatedAndLogoutRevokesLatestToken() {
        AuthDtos.TokenResponse issued = authService.signup(new AuthDtos.SignupRequest(
                "refresh@example.com",
                "password1234",
                "refresh-user"));

        AuthDtos.TokenResponse rotated = authService.refresh(
                new AuthDtos.RefreshRequest(issued.refreshToken()));

        assertThat(rotated.accessToken()).isNotEqualTo(issued.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());

        assertThatThrownBy(() -> authService.refresh(
                new AuthDtos.RefreshRequest(issued.refreshToken())))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));

        authService.logout(new AuthDtos.LogoutRequest(rotated.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(
                new AuthDtos.RefreshRequest(rotated.refreshToken())))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
    }
}
