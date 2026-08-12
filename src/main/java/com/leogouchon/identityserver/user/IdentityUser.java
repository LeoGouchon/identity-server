package com.leogouchon.identityserver.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "identity_user")
@Getter @Setter @NoArgsConstructor
public class IdentityUser {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 320)
    private String email;
    @Column(name = "first_name", length = 100)
    private String firstName;
    @Column(name = "last_name", length = 100)
    private String lastName;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    public IdentityUser(String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public IdentityUser(String email, String passwordHash, String firstName, String lastName) {
        this(email, passwordHash);
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
