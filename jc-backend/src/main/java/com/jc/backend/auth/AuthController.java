package com.jc.backend.auth;

import com.jc.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AuthDtos.TokenResponse> signup(@Valid @RequestBody AuthDtos.SignupRequest request) {
        return ApiResponse.created(authService.signup(request));
    }

    @PostMapping("/login")
    ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    ApiResponse<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    ApiResponse<AuthDtos.UserSummary> me(@AuthenticationPrincipal Jwt token) {
        return ApiResponse.ok(authService.currentUser(userId(token)));
    }

    private long userId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
