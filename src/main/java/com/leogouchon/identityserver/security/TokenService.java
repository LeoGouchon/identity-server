package com.leogouchon.identityserver.security;

import com.leogouchon.identityserver.user.IdentityUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class TokenService {
    private KeyPair keyPair;
    private final SigningKeyRepository signingKeys;
    private final String issuer;
    private final long accessTtl;

    public TokenService(@Value("${identity.issuer}") String issuer, @Value("${identity.access-token-ttl-seconds}") long accessTtl, SigningKeyRepository signingKeys) {
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.signingKeys = signingKeys;
    }

    @PostConstruct
    void initializeKey() throws Exception {
        var existing = signingKeys.findAll().stream().findFirst();
        if (existing.isPresent()) {
            var privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(existing.get().getPrivateKey())));
            var publicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(existing.get().getPublicKey())));
            keyPair = new KeyPair(publicKey, privateKey);
            return;
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        SigningKey key = new SigningKey();
        key.setId(java.util.UUID.randomUUID());
        key.setPrivateKey(java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        key.setPublicKey(java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        signingKeys.save(key);
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public String issuer() {
        return issuer;
    }

    public long accessTtl() {
        return accessTtl;
    }

    public String accessToken(IdentityUser user, String audience, String scope) {
        Instant now = Instant.now(Clock.systemUTC());
        return Jwts.builder().header().keyId("identity-rsa-1").and().issuer(issuer).subject(user.getId().toString())
                .audience().add(audience).and().issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessTtl)))
                .claim("scope", scope).claim("email", user.getEmail())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    public String idToken(IdentityUser user, String clientId, String nonce) {
        Instant now = Instant.now(Clock.systemUTC());
        var builder = Jwts.builder().header().keyId("identity-rsa-1").and().issuer(issuer).subject(user.getId().toString())
                .audience().add(clientId).and().issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessTtl)))
                .claim("email", user.getEmail()).claim("email_verified", true);
        if (nonce != null) builder.claim("nonce", nonce);
        return builder.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
