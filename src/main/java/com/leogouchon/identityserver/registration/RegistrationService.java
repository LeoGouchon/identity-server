package com.leogouchon.identityserver.registration;

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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {
    private final IdentityUserRepository users;
    private final InvitationTokenRepository invitations;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient;
    private final String provisioningUrl;
    private final String provisioningSecret;

    public RegistrationService(IdentityUserRepository users, InvitationTokenRepository invitations,
                               PasswordEncoder passwordEncoder, RestClient.Builder restClient,
                               @Value("${identity.provisioning-url}") String provisioningUrl,
                               @Value("${identity.provisioning-secret}") String provisioningSecret) {
        this.users = users;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.restClient = restClient.build();
        this.provisioningUrl = provisioningUrl;
        this.provisioningSecret = provisioningSecret;
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
        invitation.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        invitations.save(invitation);
        provisionUser(user, invitation);
        return new SignupResponse(user.getId(), user.getEmail());
    }

    public InvitationResponse createInvitation(String providedSecret, InvitationRequest request) {
        if (!provisioningSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid secret");
        }

        InvitationToken invitation = new InvitationToken();
        invitation.setToken(UUID.randomUUID() + UUID.randomUUID().toString());
        invitation.setPlayerId(request.playerId());
        invitation.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        invitations.save(invitation);
        return new InvitationResponse(invitation.getToken());
    }

    private void provisionUser(IdentityUser user, InvitationToken invitation) {
        Map<String, Object> request = new HashMap<>();
        request.put("identityUserId", user.getId());
        request.put("email", user.getEmail());
        request.put("firstName", user.getFirstName());
        request.put("lastName", user.getLastName());
        request.put("playerId", invitation.getPlayerId());
        restClient.post()
                .uri(provisioningUrl)
                .header("X-Identity-Provisioning-Secret", provisioningSecret)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
