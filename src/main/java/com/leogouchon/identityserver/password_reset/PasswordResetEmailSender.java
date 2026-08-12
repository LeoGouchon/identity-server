package com.leogouchon.identityserver.password_reset;

import com.leogouchon.identityserver.email.EmailClient;
import com.leogouchon.identityserver.email.EmailMessage;
import com.leogouchon.identityserver.email.EmailTemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PasswordResetEmailSender implements EmailSender {
    private final EmailClient emailClient;
    private final EmailTemplateRenderer templateRenderer;

    public PasswordResetEmailSender(EmailClient emailClient, EmailTemplateRenderer templateRenderer) {
        this.emailClient = emailClient;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public void sendPasswordReset(String recipient, String resetUrl) {
        send(recipient, "Réinitialisation de votre mot de passe", "email/password-reset",
                Map.of("resetUrl", resetUrl));
    }

    @Override
    public void sendPasswordChanged(String recipient) {
        send(recipient, "Votre mot de passe a été modifié", "email/password-changed", Map.of());
    }

    private void send(String recipient, String subject, String template, Map<String, Object> variables) {
        String html = templateRenderer.render(template, variables);
        emailClient.send(new EmailMessage(recipient, subject, html));
    }
}
