package com.leogouchon.identityserver.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailClient implements EmailClient {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailClient(
            JavaMailSender mailSender,
            @Value("${spring.mail.from:Identity Server <no-reply@example.com>}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage email) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Unable to compose email", exception);
        }
    }
}
