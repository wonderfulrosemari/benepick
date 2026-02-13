package com.benepick.auth.repository;

import com.benepick.auth.entity.RefreshTokenSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

    Optional<RefreshTokenSession> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    default Optional<RefreshTokenSession> findActiveSession(String refreshTokenHash) {
        return findByRefreshTokenHashAndRevokedAtIsNull(refreshTokenHash)
            .filter(session -> !session.isExpired(OffsetDateTime.now()));
    }
}
