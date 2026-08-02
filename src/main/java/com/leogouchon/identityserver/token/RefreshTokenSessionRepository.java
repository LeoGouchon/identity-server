package com.leogouchon.identityserver.token;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {
    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);
}
