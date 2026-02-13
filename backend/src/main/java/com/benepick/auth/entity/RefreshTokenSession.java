package com.benepick.auth.entity;

import com.benepick.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "refresh_token_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Builder
    private RefreshTokenSession(
        AppUser user,
        String refreshTokenHash,
        String userAgent,
        String ipAddress,
        OffsetDateTime expiresAt
    ) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    public static RefreshTokenSession issue(
        AppUser user,
        String refreshTokenHash,
        String userAgent,
        String ipAddress,
        OffsetDateTime expiresAt
    ) {
        return RefreshTokenSession.builder()
            .user(user)
            .refreshTokenHash(refreshTokenHash)
            .userAgent(userAgent)
            .ipAddress(ipAddress)
            .expiresAt(expiresAt)
            .build();
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt.isBefore(now);
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }
}
