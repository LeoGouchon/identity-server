package com.leogouchon.identityserver.password_reset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
@Getter
@NoArgsConstructor
public class PasswordResetToken {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;

    public PasswordResetToken(UUID userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void consumeAt(LocalDateTime now) {
        this.usedAt = now;
    }
}
