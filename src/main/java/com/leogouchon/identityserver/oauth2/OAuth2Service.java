package com.leogouchon.identityserver.oauth2;

import com.leogouchon.identityserver.security.TokenService;
import com.leogouchon.identityserver.token.RefreshTokenSession;
import com.leogouchon.identityserver.token.RefreshTokenSessionRepository;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuth2Service {
    private final TokenService tokens;
    private final IdentityUserRepository users;
    private final RefreshTokenSessionRepository refreshTokens;
    private final Map<String, AuthorizationCode> codes = new ConcurrentHashMap<>();
    private final String issuer;
    private final long refreshTtlDays;
    private final Set<String> allowedBackends;
    private final String defaultBackend;
    private final Map<String, Set<String>> clients;

    public OAuth2Service(TokenService tokens, IdentityUserRepository users, RefreshTokenSessionRepository refreshTokens,
                         @Value("${identity.issuer}") String issuer,
                         @Value("${identity.refresh-token-ttl-days}") long refreshTtlDays,
                         @Value("${identity.allowed-backends}") String allowedBackends,
                         @Value("${identity.oauth-clients}") String clientConfiguration) {
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.issuer = issuer;
        this.refreshTtlDays = refreshTtlDays;
        this.allowedBackends = Arrays.stream(allowedBackends.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (this.allowedBackends.isEmpty()) {
            throw new IllegalArgumentException("At least one identity backend must be configured");
        }
        this.defaultBackend = this.allowedBackends.iterator().next();
        this.clients = parseClients(clientConfiguration);
        if (this.clients.isEmpty()) {
            throw new IllegalArgumentException("At least one identity OAuth client must be configured");
        }
    }

    public Map<String, Object> discovery() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issuer", issuer);
        result.put("authorization_endpoint", issuer + "/oauth2/authorize");
        result.put("token_endpoint", issuer + "/oauth2/token");
        result.put("jwks_uri", issuer + "/oauth2/jwks");
        result.put("userinfo_endpoint", issuer + "/userinfo");
        result.put("end_session_endpoint", issuer + "/connect/logout");
        result.put("response_types_supported", List.of("code"));
        result.put("subject_types_supported", List.of("public"));
        result.put("id_token_signing_alg_values_supported", List.of("RS256"));
        result.put("scopes_supported", List.of("openid", "profile", "email", "hubscore.read"));
        result.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        result.put("code_challenge_methods_supported", List.of("S256"));
        result.put("resource_indicators_supported", true);
        return result;
    }

    public Map<String, Object> jwks() {
        var rsa = (java.security.interfaces.RSAPublicKey) tokens.keyPair().getPublic();
        return Map.of("keys", List.of(Map.of("kty", "RSA", "use", "sig", "alg", "RS256", "kid", "identity-rsa-1",
                "n", unsigned(rsa.getModulus().toByteArray()), "e", unsigned(rsa.getPublicExponent().toByteArray()))));
    }

    public String authorize(String responseType, String clientId, String redirectUri, String scope, String state,
                            String nonce, String codeChallenge, String codeChallengeMethod, String resource,
                            Authentication authentication) {
        if (!"code".equals(responseType)
                || !clients.getOrDefault(clientId, Set.of()).contains(redirectUri)
                || !"S256".equals(codeChallengeMethod)) {
            throw new OAuthException("invalid_request");
        }

        String backend = resource == null || resource.isBlank() ? defaultBackend : resource;
        if (!allowedBackends.contains(backend)) {
            throw new OAuthException("invalid_target");
        }

        IdentityUser user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        String code = UUID.randomUUID().toString();
        codes.put(code, new AuthorizationCode(user.getId(), clientId, redirectUri, scope, nonce, codeChallenge,
                backend, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(2)));
        return redirectUri + "?code=" + code + "&state=" + state;
    }

    public Map<String, Object> token(String grantType, String clientId, String code, String redirectUri,
                                     String codeVerifier, String refreshToken) {
        if ("authorization_code".equals(grantType)) {
            return authorizationCode(clientId, code, redirectUri, codeVerifier);
        }
        if ("refresh_token".equals(grantType)) {
            return refresh(clientId, refreshToken);
        }
        throw new OAuthException("unsupported_grant_type");
    }

    public Map<String, Object> userinfo(Authentication authentication) {
        UUID subject = authentication.getPrincipal() instanceof Jwt jwt
                ? UUID.fromString(jwt.getSubject())
                : users.findByEmailIgnoreCase(authentication.getName()).orElseThrow().getId();
        IdentityUser user = users.findById(subject).orElseThrow();
        return Map.of("sub", user.getId().toString(), "email", user.getEmail(), "email_verified", true);
    }

    public String logout(String postLogoutRedirectUri) {
        return postLogoutRedirectUri == null ? "/" : postLogoutRedirectUri;
    }

    public void revoke(String token) {
        refreshTokens.findByTokenHash(hash(token)).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
            refreshTokens.save(session);
        });
    }

    private Map<String, Object> authorizationCode(String clientId, String code, String redirectUri, String verifier) {
        AuthorizationCode stored = codes.remove(code);
        if (stored == null
                || stored.expiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))
                || !stored.clientId().equals(clientId)
                || !stored.redirectUri().equals(redirectUri)
                || verifier == null
                || !pkce(verifier).equals(stored.challenge())) {
            throw new OAuthException("invalid_grant");
        }
        return issue(users.findById(stored.userId()).orElseThrow(), clientId, stored.scope(), stored.nonce(), stored.audience());
    }

    private Map<String, Object> refresh(String clientId, String raw) {
        if (raw == null) {
            throw new OAuthException("invalid_grant");
        }
        RefreshTokenSession current = refreshTokens.findByTokenHash(hash(raw))
                .orElseThrow(() -> new OAuthException("invalid_grant"));
        if (!current.getClientId().equals(clientId)
                || current.getRevokedAt() != null
                || current.getUsedAt() != null
                || current.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new OAuthException("invalid_grant");
        }
        current.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        current.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        refreshTokens.save(current);
        return issue(current.getUser(), clientId, current.getScope(), null, current.getAudience());
    }

    private Map<String, Object> issue(IdentityUser user, String clientId, String scope, String nonce, String audience) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshTokenSession session = new RefreshTokenSession();
        session.setTokenHash(hash(raw));
        session.setUser(user);
        session.setClientId(clientId);
        session.setScope(scope);
        session.setAudience(audience);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(refreshTtlDays));
        refreshTokens.save(session);
        return Map.of("access_token", tokens.accessToken(user, audience, scope),
                "id_token", tokens.idToken(user, clientId, nonce),
                "refresh_token", raw, "token_type", "Bearer", "expires_in", tokens.accessTtl(), "scope", scope);
    }

    private static Map<String, Set<String>> parseClients(String configuration) {
        Map<String, Set<String>> parsed = new LinkedHashMap<>();
        for (String entry : configuration.split(";")) {
            String[] client = entry.trim().split("=", 2);
            if (client.length != 2 || client[0].isBlank() || client[1].isBlank()) {
                throw new IllegalArgumentException("Invalid identity.oauth-clients entry: " + entry);
            }
            Set<String> redirectUris = Arrays.stream(client[1].split("\\|"))
                    .map(String::trim)
                    .filter(uri -> !uri.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (redirectUris.isEmpty() || parsed.put(client[0].trim(), redirectUris) != null) {
                throw new IllegalArgumentException("Invalid or duplicate identity OAuth client: " + client[0]);
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    private static String pkce(String value) {
        return b64(digest("SHA-256", value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String hash(String value) {
        return b64(digest("SHA-256", value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] digest(String algorithm, byte[] value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String unsigned(byte[] value) {
        int offset = value.length > 1 && value[0] == 0 ? 1 : 0;
        return b64(Arrays.copyOfRange(value, offset, value.length));
    }

    private record AuthorizationCode(UUID userId, String clientId, String redirectUri, String scope, String nonce,
                                     String challenge, String audience, LocalDateTime expiresAt) {
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    static class OAuthException extends RuntimeException {
        OAuthException(String message) {
            super(message);
        }
    }
}
