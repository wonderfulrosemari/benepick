package com.benepick.auth.service;

import com.benepick.auth.entity.AppUser;
import com.benepick.auth.entity.AuthProvider;
import com.benepick.auth.entity.RefreshTokenSession;
import com.benepick.auth.entity.UserAuthProvider;
import com.benepick.auth.repository.AppUserRepository;
import com.benepick.auth.repository.RefreshTokenSessionRepository;
import com.benepick.auth.repository.UserAuthProviderRepository;
import com.benepick.auth.security.JwtProvider;
import com.benepick.auth.security.TokenHashService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final JwtProvider jwtProvider;
    private final TokenHashService tokenHashService;

    public AuthService(
        AppUserRepository appUserRepository,
        UserAuthProviderRepository userAuthProviderRepository,
        RefreshTokenSessionRepository refreshTokenSessionRepository,
        GoogleIdTokenVerifierService googleIdTokenVerifierService,
        JwtProvider jwtProvider,
        TokenHashService tokenHashService
    ) {
        this.appUserRepository = appUserRepository;
        this.userAuthProviderRepository = userAuthProviderRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.jwtProvider = jwtProvider;
        this.tokenHashService = tokenHashService;
    }

    @Transactional
    public AuthTokens loginWithGoogle(String idToken, String userAgent, String ipAddress) {
        GoogleUserInfo googleUserInfo = googleIdTokenVerifierService.verify(idToken);
        AppUser user = findOrCreateGoogleUser(googleUserInfo);
        return issueTokens(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken, String userAgent, String ipAddress) {
        UUID tokenUserId = parseRefreshTokenSubject(refreshToken);
        String refreshTokenHash = tokenHashService.sha256(refreshToken);

        RefreshTokenSession session = refreshTokenSessionRepository
            .findActiveSession(refreshTokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"));

        if (!session.getUser().getId().equals(tokenUserId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token owner mismatch");
        }

        session.revoke();

        return issueTokens(session.getUser(), userAgent, ipAddress);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Jws<Claims> claims = jwtProvider.parse(refreshToken);
            if (!jwtProvider.isRefreshToken(claims)) {
                return;
            }

            String hash = tokenHashService.sha256(refreshToken);
            refreshTokenSessionRepository
                .findByRefreshTokenHashAndRevokedAtIsNull(hash)
                .ifPresent(RefreshTokenSession::revoke);
        } catch (JwtException | IllegalArgumentException ignored) {
            // already invalid token
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserInfo getCurrentUser(UUID userId) {
        AppUser user = appUserRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return new CurrentUserInfo(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getProfileImageUrl(),
            user.getRole().name()
        );
    }

    private AppUser findOrCreateGoogleUser(GoogleUserInfo info) {
        return userAuthProviderRepository
            .findByProviderAndProviderUserId(AuthProvider.GOOGLE, info.providerUserId())
            .map(existingProvider -> {
                AppUser user = existingProvider.getUser();
                user.updateProfile(info.name(), info.profileImageUrl());
                return user;
            })
            .orElseGet(() -> {
                AppUser user = appUserRepository
                    .findByEmail(info.email())
                    .map(existingUser -> {
                        existingUser.updateProfile(info.name(), info.profileImageUrl());
                        return existingUser;
                    })
                    .orElseGet(() -> appUserRepository.save(AppUser.newUser(info.email(), info.name(), info.profileImageUrl())));

                if (!userAuthProviderRepository.existsByUser_IdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
                    userAuthProviderRepository.save(UserAuthProvider.google(user, info.providerUserId()));
                }

                return user;
            });
    }

    private AuthTokens issueTokens(AppUser user, String userAgent, String ipAddress) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        OffsetDateTime refreshExpiresAt = OffsetDateTime.now(ZoneOffset.UTC)
            .plusSeconds(jwtProvider.refreshTokenExpiresInSeconds());

        RefreshTokenSession session = RefreshTokenSession.issue(
            user,
            tokenHashService.sha256(refreshToken),
            userAgent,
            ipAddress,
            refreshExpiresAt
        );
        refreshTokenSessionRepository.save(session);

        return new AuthTokens(
            accessToken,
            jwtProvider.accessTokenExpiresInSeconds(),
            refreshToken,
            jwtProvider.refreshTokenExpiresInSeconds()
        );
    }

    private UUID parseRefreshTokenSubject(String refreshToken) {
        try {
            Jws<Claims> claims = jwtProvider.parse(refreshToken);
            if (!jwtProvider.isRefreshToken(claims)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token type is not refresh");
            }
            return UUID.fromString(claims.getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        }
    }
}
