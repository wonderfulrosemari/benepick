package com.benepick.auth.entity;

import com.benepick.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(length = 100)
    private String name;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private AppUser(String email, String name, String profileImageUrl, UserRole role, boolean active) {
        this.email = email;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.role = role;
        this.active = active;
    }

    public static AppUser newUser(String email, String name, String profileImageUrl) {
        return AppUser.builder()
            .email(email)
            .name(name)
            .profileImageUrl(profileImageUrl)
            .role(UserRole.USER)
            .active(true)
            .build();
    }

    public void updateProfile(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }
}
