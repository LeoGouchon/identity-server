package com.leogouchon.identityserver.password_reset;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${spring.mail.from:Identity Server <no-reply@example.com>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendPasswordReset(String recipient, String resetUrl) {
        send(recipient, "Réinitialisation de votre mot de passe",
                "<p>Pour choisir un nouveau mot de passe, cliquez sur ce lien :</p><p><a href=\"" + resetUrl
                        + "\">Réinitialiser mon mot de passe</a></p><p>Ce lien expire dans 30 minutes.</p>");
    }

    @Override
    public void sendPasswordChanged(String recipient) {
        send(recipient, "Votre mot de passe a été modifié",
                "<p>Votre mot de passe Identity Server vient d’être modifié.</p><p>Si vous n’êtes pas à l’origine de cette action, contactez immédiatement l’équipe support.</p>");
    }

    private void send(String recipient, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Unable to compose password email", exception);
        }
    }
}
