package com.benepick.auth.service;

import com.benepick.auth.security.GoogleAuthProperties;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GoogleIdTokenVerifierService {

    private static final List<String> VALID_ISSUERS = List.of(
        "https://accounts.google.com",
        "accounts.google.com"
    );

    private final GoogleAuthProperties properties;
    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifierService(GoogleAuthProperties properties) {
        this.properties = properties;

        if (properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()) {
            throw new IllegalStateException("auth.google.jwk-set-uri is required");
        }

        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    }

    public GoogleUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is required");
        }

        if (properties.clientId() == null || properties.clientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Google client id is not configured");
        }

        final Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
        }

        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : jwt.getClaimAsString("iss");
        if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google issuer");
        }

        if (jwt.getAudience() == null || !jwt.getAudience().contains(properties.clientId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google audience mismatch");
        }

        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (Boolean.FALSE.equals(emailVerified)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token claims are invalid");
        }

        return new GoogleUserInfo(subject, email, name, picture);
    }
}
