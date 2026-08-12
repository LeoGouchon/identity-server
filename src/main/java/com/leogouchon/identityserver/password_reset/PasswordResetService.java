package com.leogouchon.identityserver.password_reset;

import com.leogouchon.identityserver.config.IdentityProperties;
import com.leogouchon.identityserver.password_reset.dto.*;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private static final String GENERIC_MESSAGE = "Si cette adresse correspond à un compte, un lien de réinitialisation a été envoyé.";
    private final IdentityUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final EmailSender emailSender;
    private final IdentityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            IdentityUserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder encoder,
            EmailSender emailSender,
            IdentityProperties properties
    ) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Transactional
    public PasswordResetResponse requestReset(ForgotPasswordRequest request) {
        users.findByEmailIgnoreCase(
                request.email()).ifPresent(user -> {
                    String rawToken = generateToken();
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

                    tokens.deleteByUserIdAndUsedAtIsNull(user.getId());
                    tokens.save(new PasswordResetToken(user.getId(), hash(rawToken),
                            now.plusMinutes(properties.getPasswordReset().getTokenTtlMinutes()), now));

                    String url = properties.getAuthFrontendUrl()
                            + "/forgot-password?email="
                            + java.net.URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                            + "&token="
                            + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
                    emailSender.sendPasswordReset(user.getEmail(), url);
                }
        );
        return new PasswordResetResponse(GENERIC_MESSAGE);
    }

    @Transactional
    public PasswordResetResponse resetPassword(ResetPasswordRequest request) {
        if (request.password() == null || request.password().length() < 8)
            throw invalidToken();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (request.token() == null || request.email() == null)
            throw invalidToken();

        PasswordResetToken token = tokens.findByTokenHash(hash(request.token())).orElseThrow(this::invalidToken);

        if (!token.isUsableAt(now))
            throw invalidToken();

        IdentityUser user = users.findById(token.getUserId()).filter(IdentityUser::isEnabled).orElseThrow(this::invalidToken);

        if (!user.getEmail().equalsIgnoreCase(request.email()))
            throw invalidToken();

        user.changePassword(encoder.encode(request.password()));
        token.consumeAt(now);
        emailSender.sendPasswordChanged(user.getEmail());

        return new PasswordResetResponse("Votre mot de passe a été modifié. Vous pouvez maintenant vous connecter.");
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré");
    }

    private String generateToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
