package com.benepick.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, String email, String role) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiration = now.plusMinutes(properties.accessTokenMinutes());

        return Jwts.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(expiration.toInstant()))
            .claim("email", email)
            .claim("role", role)
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .signWith(signingKey)
            .compact();
    }

    public String createRefreshToken(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiration = now.plusDays(properties.refreshTokenDays());

        return Jwts.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(expiration.toInstant()))
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .signWith(signingKey)
            .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
    }

    public boolean isAccessToken(Jws<Claims> claims) {
        return TYPE_ACCESS.equals(claims.getPayload().get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Jws<Claims> claims) {
        return TYPE_REFRESH.equals(claims.getPayload().get(CLAIM_TYPE, String.class));
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    public long refreshTokenExpiresInSeconds() {
        return properties.refreshTokenDays() * 24 * 60 * 60;
    }
}
