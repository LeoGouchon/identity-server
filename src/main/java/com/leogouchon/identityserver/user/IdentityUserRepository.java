package com.leogouchon.identityserver.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, UUID> {
    Optional<IdentityUser> findByEmailIgnoreCase(String email);
}
