package com.benepick.auth.entity;

import com.benepick.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "user_auth_provider",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_user", columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_user_provider", columnNames = {"user_id", "provider"})
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAuthProvider extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 200)
    private String providerUserId;

    @Builder
    private UserAuthProvider(AppUser user, AuthProvider provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static UserAuthProvider google(AppUser user, String providerUserId) {
        return UserAuthProvider.builder()
            .user(user)
            .provider(AuthProvider.GOOGLE)
            .providerUserId(providerUserId)
            .build();
    }
}
