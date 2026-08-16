package com.leogouchon.identityserver.invitation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "invitation_token")
@Getter @Setter @NoArgsConstructor
public class InvitationToken {
    @Id @GeneratedValue
    private UUID id;
    @Column(nullable = false, unique = true, length = 128)
    private String token;
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;
    @Column(name = "player_id")
    private UUID playerId;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
}
