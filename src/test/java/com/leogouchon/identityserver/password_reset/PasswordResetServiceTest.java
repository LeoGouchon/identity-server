package com.leogouchon.identityserver.password_reset;

import com.leogouchon.identityserver.config.IdentityProperties;
import com.leogouchon.identityserver.password_reset.dto.ForgotPasswordRequest;
import com.leogouchon.identityserver.password_reset.dto.ResetPasswordRequest;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock IdentityUserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock EmailSender emails;
    private PasswordResetService service;
    private IdentityUser user;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties();
        properties.setAuthFrontendUrl("http://localhost:5180");
        properties.getPasswordReset().setTokenTtlMinutes(30);
        service = new PasswordResetService(users, tokens, encoder, emails, properties);
        user = new IdentityUser("user@example.com", "old-hash");
    }

    @Test
    void requestResetSendsLinkAndDoesNotRevealUnknownAddresses() {
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        assertTrue(service.requestReset(new ForgotPasswordRequest("user@example.com")).message().contains("envoyé"));
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emails).sendPasswordReset(eq(user.getEmail()), url.capture());
        assertTrue(url.getValue().contains("token="));

        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        service.requestReset(new ForgotPasswordRequest("missing@example.com"));
        verify(emails, times(1)).sendPasswordReset(any(), any());
    }

    @Test
    void invalidOrExpiredTokenIsRejected() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(new PasswordResetToken(
                user.getId(), "hash", LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusMinutes(2))));
        assertThrows(RuntimeException.class, () -> service.resetPassword(
                new ResetPasswordRequest(user.getEmail(), "token", "new-password")));
        verifyNoInteractions(encoder, emails);
    }
}
