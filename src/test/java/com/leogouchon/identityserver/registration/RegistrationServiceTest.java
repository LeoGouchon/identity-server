package com.leogouchon.identityserver.registration;

import com.leogouchon.identityserver.invitation.InvitationToken;
import com.leogouchon.identityserver.invitation.InvitationTokenRepository;
import com.leogouchon.identityserver.registration.dto.InvitationRequest;
import com.leogouchon.identityserver.registration.dto.SignupRequest;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {
    @Mock IdentityUserRepository users;
    @Mock InvitationTokenRepository invitations;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RestClient.Builder restClientBuilder;
    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec requestBody;
    @Mock RestClient.ResponseSpec response;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(requestBody);
        when(requestBody.uri(any(String.class))).thenReturn(requestBody);
        when(requestBody.header(any(String.class), any(String.class))).thenReturn(requestBody);
        when(requestBody.body(any(Object.class))).thenReturn(requestBody);
        when(requestBody.retrieve()).thenReturn(response);
        service = new RegistrationService(users, invitations, passwordEncoder, restClientBuilder,
                "http://api.example/internal/users", "secret");
    }

    @Test
    void createInvitationRequiresSecretAndPersistsSevenDayInvitation() {
        UUID playerId = UUID.randomUUID();
        var response = service.createInvitation("secret", new InvitationRequest(playerId));

        assertNotNull(response.token());
        verify(invitations).save(argThat(invitation -> invitation.getPlayerId().equals(playerId)
                && invitation.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(6))));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.createInvitation("wrong", new InvitationRequest(playerId)));
    }

    @Test
    void signupCreatesUserConsumesInvitationAndProvisionsDownstreamUser() {
        InvitationToken invitation = new InvitationToken();
        invitation.setToken("invitation");
        invitation.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        invitation.setPlayerId(UUID.randomUUID());
        when(invitations.findByToken("invitation")).thenReturn(Optional.of(invitation));
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded");

        var response = service.signup(new SignupRequest("user@example.com", "password", "invitation"));

        assertEquals("user@example.com", response.email());
        verify(users).save(argThat(user -> user.getEmail().equals("user@example.com") && user.getPasswordHash().equals("encoded")));
        verify(invitations).save(argThat(value -> value.getUsedAt() != null));
    }

    @Test
    void signupRejectsMissingExpiredOrUsedInvitationAndDuplicateEmail() {
        when(invitations.findByToken("missing")).thenReturn(Optional.empty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.signup(new SignupRequest("a@example.com", "password", "missing")));

        InvitationToken expired = new InvitationToken();
        expired.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(invitations.findByToken("expired")).thenReturn(Optional.of(expired));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.signup(new SignupRequest("a@example.com", "password", "expired")));

        InvitationToken valid = new InvitationToken();
        valid.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(invitations.findByToken("duplicate")).thenReturn(Optional.of(valid));
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(new IdentityUser()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.signup(new SignupRequest("a@example.com", "password", "duplicate")));
    }
}
