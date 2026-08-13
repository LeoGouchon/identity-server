package com.leogouchon.identityserver.registration;

import com.leogouchon.identityserver.config.IdentityProperties;
import com.leogouchon.identityserver.invitation.InvitationToken;
import com.leogouchon.identityserver.invitation.InvitationTokenRepository;
import com.leogouchon.identityserver.registration.dto.InvitationRequest;
import com.leogouchon.identityserver.registration.dto.InvitationResponse;
import com.leogouchon.identityserver.registration.dto.SignupRequest;
import com.leogouchon.identityserver.registration.dto.SignupResponse;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RegistrationService {
    private final IdentityUserRepository users;
    private final InvitationTokenRepository invitations;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient;
    private final String provisioningSecret;
    private final String authFrontendUrl;
    private final Map<String, IdentityProperties.OAuthClient> clients;

    public RegistrationService(IdentityUserRepository users, InvitationTokenRepository invitations,
                               PasswordEncoder passwordEncoder, RestClient.Builder restClient,
                               @Value("${identity.provisioning-secret}") String provisioningSecret,
                               @Value("${identity.auth-frontend-url:http://localhost:5180}") String authFrontendUrl,
                               IdentityProperties properties) {
        this.users = users;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.restClient = restClient.build();
        this.provisioningSecret = provisioningSecret;
        this.authFrontendUrl = authFrontendUrl.replaceAll("/$", "");
        this.clients = properties.getOauthClients().stream().collect(Collectors.toUnmodifiableMap(
                client -> client.getClientId().trim(), Function.identity()));
    }

    public SignupResponse signup(SignupRequest request) {
        InvitationToken invitation = invitations.findByToken(request.invitationToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation"));
        if (invitation.getUsedAt() != null || invitation.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation expired or used");
        }
        if (users.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        IdentityUser user = new IdentityUser(request.email(), passwordEncoder.encode(request.password()),
                request.firstName(), request.lastName());
        users.save(user);
        return new SignupResponse(user.getId(), user.getEmail());
    }

    public InvitationResponse createInvitation(String providedSecret, InvitationRequest request) {
        if (!provisioningSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid secret");
        }

        IdentityProperties.OAuthClient client = client(request.clientId());
        if (client.getProvisioningUrl() == null || client.getProvisioningUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application has no provisioning endpoint");
        }
        InvitationToken invitation = new InvitationToken();
        invitation.setToken(UUID.randomUUID() + UUID.randomUUID().toString());
        invitation.setClientId(client.getClientId().trim());
        invitation.setPlayerId(request.playerId());
        invitation.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        invitations.save(invitation);
        return invitationResponse(invitation.getToken(), client);
    }

    public InvitationResponse invitation(String token) {
        InvitationToken invitation = validInvitation(token);
        IdentityProperties.OAuthClient client = client(invitation.getClientId());
        return invitationResponse(token, client);
    }

    public InvitationResponse claim(String token, Authentication authentication) {
        InvitationToken invitation = validInvitation(token);
        IdentityUser user = authenticatedUser(authentication);
        provisionUser(user, invitation);
        invitation.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        invitations.save(invitation);
        return invitationResponse(token, client(invitation.getClientId()));
    }

    private void provisionUser(IdentityUser user, InvitationToken invitation) {
        Map<String, Object> request = new HashMap<>();
        request.put("identityUserId", user.getId());
        request.put("email", user.getEmail());
        request.put("firstName", user.getFirstName());
        request.put("lastName", user.getLastName());
        request.put("playerId", invitation.getPlayerId());
        IdentityProperties.OAuthClient client = client(invitation.getClientId());
        restClient.post()
                .uri(client.getProvisioningUrl())
                .header("X-Identity-Provisioning-Secret", provisioningSecret)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private InvitationToken validInvitation(String token) {
        InvitationToken invitation = invitations.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation"));
        if (invitation.getUsedAt() != null || invitation.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation expired or used");
        }
        return invitation;
    }

    private IdentityUser authenticatedUser(Authentication authentication) {
        UUID id = authentication.getPrincipal() instanceof Jwt jwt
                ? UUID.fromString(jwt.getSubject())
                : users.findByEmailIgnoreCase(authentication.getName()).orElseThrow().getId();
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private IdentityProperties.OAuthClient client(String clientId) {
        if (clientId == null || !clients.containsKey(clientId.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown application");
        }
        return clients.get(clientId.trim());
    }

    private static String applicationName(IdentityProperties.OAuthClient client) {
        return client.getDisplayName() == null || client.getDisplayName().isBlank()
                ? client.getClientId() : client.getDisplayName();
    }

    private InvitationResponse invitationResponse(String token, IdentityProperties.OAuthClient client) {
        String invitationUrl = authFrontendUrl + "/signup?invitationToken="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        return new InvitationResponse(token, client.getClientId(), applicationName(client), invitationUrl);
    }
}
