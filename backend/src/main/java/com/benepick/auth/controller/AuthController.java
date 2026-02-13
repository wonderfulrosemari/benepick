package com.benepick.auth.controller;

import com.benepick.auth.dto.AuthTokenResponse;
import com.benepick.auth.dto.GoogleLoginRequest;
import com.benepick.auth.dto.MeResponse;
import com.benepick.auth.dto.MessageResponse;
import com.benepick.auth.security.RefreshTokenCookieManager;
import com.benepick.auth.security.UserPrincipal;
import com.benepick.auth.service.AuthService;
import com.benepick.auth.service.AuthTokens;
import com.benepick.auth.service.CurrentUserInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    public AuthController(AuthService authService, RefreshTokenCookieManager refreshTokenCookieManager) {
        this.authService = authService;
        this.refreshTokenCookieManager = refreshTokenCookieManager;
    }

    @PostMapping("/google")
    public AuthTokenResponse googleLogin(
        @Valid @RequestBody GoogleLoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        AuthTokens authTokens = authService.loginWithGoogle(
            request.idToken(),
            extractUserAgent(httpServletRequest),
            extractClientIp(httpServletRequest)
        );

        refreshTokenCookieManager.writeRefreshToken(
            httpServletResponse,
            authTokens.refreshToken(),
            authTokens.refreshTokenExpiresInSeconds()
        );

        return new AuthTokenResponse(authTokens.accessToken(), authTokens.accessTokenExpiresInSeconds());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = refreshTokenCookieManager
            .extractRefreshToken(request)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token cookie is missing"));

        AuthTokens authTokens = authService.refresh(
            refreshToken,
            extractUserAgent(request),
            extractClientIp(request)
        );

        refreshTokenCookieManager.writeRefreshToken(
            response,
            authTokens.refreshToken(),
            authTokens.refreshTokenExpiresInSeconds()
        );

        return new AuthTokenResponse(authTokens.accessToken(), authTokens.accessTokenExpiresInSeconds());
    }

    @PostMapping("/logout")
    public MessageResponse logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenCookieManager.extractRefreshToken(request).ifPresent(authService::logout);
        refreshTokenCookieManager.clearRefreshToken(response);
        return new MessageResponse("logged out");
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        CurrentUserInfo currentUser = authService.getCurrentUser(principal.userId());
        return new MeResponse(
            currentUser.userId(),
            currentUser.email(),
            currentUser.name(),
            currentUser.profileImageUrl(),
            currentUser.role()
        );
    }

    private String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] ips = forwardedFor.split(",");
        return ips[0].trim();
    }
}
