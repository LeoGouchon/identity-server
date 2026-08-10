package com.leogouchon.identityserver.token;

import com.leogouchon.identityserver.user.IdentityUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_session")
@Getter @Setter @NoArgsConstructor
public class RefreshTokenSession {
    @Id @GeneratedValue
    private UUID id;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private IdentityUser user;
    @Column(name = "client_id", nullable = false)
    private String clientId;
    @Column(nullable = false, length = 1000)
    private String scope;
    @Column(nullable = false, length = 255)
    private String audience;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
}
