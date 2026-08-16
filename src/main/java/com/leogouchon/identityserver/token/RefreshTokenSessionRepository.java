package com.leogouchon.identityserver.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);
}
