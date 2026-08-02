package com.leogouchon.identityserver.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> { }
