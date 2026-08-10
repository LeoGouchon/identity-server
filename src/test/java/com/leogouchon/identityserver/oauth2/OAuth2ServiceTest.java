package com.leogouchon.identityserver.oauth2;

import com.leogouchon.identityserver.config.IdentityProperties;
import com.leogouchon.identityserver.security.TokenService;
import com.leogouchon.identityserver.token.RefreshTokenSession;
import com.leogouchon.identityserver.token.RefreshTokenSessionRepository;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2ServiceTest {
    @Mock TokenService tokens;
    @Mock IdentityUserRepository users;
    @Mock RefreshTokenSessionRepository refreshTokens;
    @Mock Authentication authentication;

    private OAuth2Service service;
    private IdentityUser user;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties();
        properties.setAllowedBackends(List.of("default-api"));
        properties.setScopes(List.of("openid", "profile", "email"));
        IdentityProperties.OAuthClient client = new IdentityProperties.OAuthClient();
        client.setClientId("web-client");
        client.setRedirectUris(List.of("http://localhost:4200/auth/callback"));
        IdentityProperties.OAuthClient adminClient = new IdentityProperties.OAuthClient();
        adminClient.setClientId("admin-web");
        adminClient.setRedirectUris(List.of("http://localhost:5173/auth/callback"));
        properties.setOauthClients(List.of(client, adminClient));
        service = new OAuth2Service(tokens, users, refreshTokens, "http://localhost:8081", 30, properties);
        user = new IdentityUser("user@example.com", "hash");
        when(authentication.getName()).thenReturn(user.getEmail());
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokens.accessTtl()).thenReturn(600L);
        when(tokens.accessToken(any(), any(), any())).thenReturn("access-token");
        when(tokens.idToken(any(), any(), any())).thenReturn("id-token");
        try {
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            when(tokens.keyPair()).thenReturn(generator.generateKeyPair());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void discoveryAdvertisesConfiguredEndpointsAndScopes() {
        Map<String, Object> discovery = service.discovery();

        assertEquals("http://localhost:8081", discovery.get("issuer"));
        assertEquals(List.of("openid", "profile", "email"), discovery.get("scopes_supported"));
        assertEquals(true, discovery.get("resource_indicators_supported"));
        assertEquals("http://localhost:8081/oauth2/jwks", discovery.get("jwks_uri"));
    }

    @Test
    void jwksExposesTheConfiguredSigningKey() {
        Map<String, Object> jwks = service.jwks();

        assertEquals("RSA", ((Map<?, ?>) ((List<?>) jwks.get("keys")).getFirst()).get("kty"));
        assertEquals("RS256", ((Map<?, ?>) ((List<?>) jwks.get("keys")).getFirst()).get("alg"));
    }

    @Test
    void authorizeCreatesCodeForConfiguredBackendAndRedirects() {
        String redirect = service.authorize("code", "web-client", "http://localhost:4200/auth/callback",
                "openid", "state", "nonce", pkce("verifier"), "S256", "default-api", authentication);

        assertTrue(redirect.startsWith("http://localhost:4200/auth/callback?code="));
        assertTrue(redirect.endsWith("&state=state"));
    }

    @Test
    void ssoSessionCanAuthorizeBothConfiguredFrontendClients() {
        String webRedirect = service.authorize("code", "web-client", "http://localhost:4200/auth/callback",
                "openid profile email", "web-state", "web-nonce", pkce("web-verifier"), "S256", null, authentication);
        String adminRedirect = service.authorize("code", "admin-web", "http://localhost:5173/auth/callback",
                "openid profile email", "admin-state", "admin-nonce", pkce("admin-verifier"), "S256", null, authentication);

        assertTrue(webRedirect.startsWith("http://localhost:4200/auth/callback?code="));
        assertTrue(webRedirect.endsWith("&state=web-state"));
        assertTrue(adminRedirect.startsWith("http://localhost:5173/auth/callback?code="));
        assertTrue(adminRedirect.endsWith("&state=admin-state"));
        verify(authentication, times(2)).getName();
    }

    @Test
    void authorizationCodeIsSingleUseAndReturnsConfiguredTokens() {
        String redirect = service.authorize("code", "web-client", "http://localhost:4200/auth/callback",
                "openid", "state", "nonce", pkce("verifier"), "S256", null, authentication);
        String code = redirect.substring(redirect.indexOf("code=") + 5, redirect.indexOf("&state"));

        Map<String, Object> result = service.token("authorization_code", "web-client", code,
                "http://localhost:4200/auth/callback", "verifier", null);

        assertEquals("access-token", result.get("access_token"));
        assertEquals("id-token", result.get("id_token"));
        assertEquals("openid", result.get("scope"));
        assertThrows(RuntimeException.class, () -> service.token("authorization_code", "web-client", code,
                "http://localhost:4200/auth/callback", "verifier", null));
        verify(refreshTokens).save(any(RefreshTokenSession.class));
    }

    @Test
    void refreshRotatesTokenAndPreservesAudience() {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenHash(hash("refresh-token"));
        session.setUser(user);
        session.setClientId("web-client");
        session.setScope("openid");
        session.setAudience("default-api");
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(refreshTokens.findByTokenHash(hash("refresh-token"))).thenReturn(Optional.of(session));

        Map<String, Object> result = service.token("refresh_token", "web-client", null, null, null, "refresh-token");

        assertEquals("access-token", result.get("access_token"));
        assertNotNull(session.getUsedAt());
        assertNotNull(session.getRevokedAt());
        verify(tokens).accessToken(user, "default-api", "openid");
        verify(refreshTokens, times(2)).save(any(RefreshTokenSession.class));
    }

    @Test
    void userinfoSupportsJwtAndLogoutValidatesRedirectAndPreservesState() {
        when(authentication.getPrincipal()).thenReturn(null);
        assertEquals(user.getId().toString(), service.userinfo(authentication).get("sub"));
        assertEquals("/", service.logout(null, null, null));
        assertEquals("http://localhost:4200/auth/callback?state=logout-state",
                service.logout("web-client", "http://localhost:4200/auth/callback", "logout-state"));
        assertThrows(RuntimeException.class,
                () -> service.logout("web-client", "https://attacker.example", null));
    }

    @Test
    void revokeMarksMatchingRefreshSessionAsRevoked() {
        RefreshTokenSession session = new RefreshTokenSession();
        when(refreshTokens.findByTokenHash(hash("refresh-token"))).thenReturn(Optional.of(session));

        service.revoke("refresh-token");

        assertNotNull(session.getRevokedAt());
        verify(refreshTokens).save(session);
    }

    @Test
    void invalidBackendAndClientAreRejected() {
        assertThrows(RuntimeException.class, () -> service.authorize("code", "unknown", "http://localhost:4200/auth/callback",
                "openid", "state", "nonce", "challenge", "S256", "default-api", authentication));
        assertThrows(RuntimeException.class, () -> service.authorize("code", "web-client", "http://localhost:4200/auth/callback",
                "openid", "state", "nonce", "challenge", "S256", "unknown-api", authentication));
    }

    private static String pkce(String verifier) {
        try {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String hash(String value) {
        try {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
