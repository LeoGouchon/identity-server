package com.leogouchon.identityserver.security;

import com.leogouchon.identityserver.user.IdentityUser;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {
    @Mock SigningKeyRepository signingKeys;
    private TokenService service;
    private IdentityUser user;

    @BeforeEach
    void setUp() throws Exception {
        service = new TokenService("http://localhost:8081", 600, signingKeys);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        setKeyPair(generator.generateKeyPair());
        user = new IdentityUser("user@example.com", "hash");
    }

    @Test
    void accessTokenContainsIssuerSubjectAudienceScopeAndExpiry() {
        String token = service.accessToken(user, "default-api", "openid profile email");
        var claims = Jwts.parser().verifyWith((java.security.interfaces.RSAPublicKey) service.keyPair().getPublic())
                .build().parseSignedClaims(token).getPayload();

        assertEquals("http://localhost:8081", claims.getIssuer());
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals("default-api", claims.getAudience().iterator().next());
        assertEquals("openid profile email", claims.get("scope"));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void idTokenContainsClientAndOptionalNonce() {
        String token = service.idToken(user, "web-client", "nonce");
        var claims = Jwts.parser().verifyWith((java.security.interfaces.RSAPublicKey) service.keyPair().getPublic())
                .build().parseSignedClaims(token).getPayload();

        assertEquals("web-client", claims.getAudience().iterator().next());
        assertEquals("nonce", claims.get("nonce"));
        assertEquals(true, claims.get("email_verified"));
    }

    private void setKeyPair(KeyPair keyPair) throws Exception {
        var field = TokenService.class.getDeclaredField("keyPair");
        field.setAccessible(true);
        field.set(service, keyPair);
    }
}
