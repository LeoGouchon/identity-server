package com.leogouchon.identityserver.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "signing_key") @Getter @Setter @NoArgsConstructor
public class SigningKey { @Id private UUID id; private String privateKey; private String publicKey; }
