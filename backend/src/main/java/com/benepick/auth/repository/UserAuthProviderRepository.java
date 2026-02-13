package com.benepick.auth.repository;

import com.benepick.auth.entity.AuthProvider;
import com.benepick.auth.entity.UserAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {

    Optional<UserAuthProvider> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByUser_IdAndProvider(UUID userId, AuthProvider provider);
}
