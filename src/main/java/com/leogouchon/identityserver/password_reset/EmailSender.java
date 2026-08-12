package com.leogouchon.identityserver.password_reset;

public interface EmailSender {
    void sendPasswordReset(String recipient, String resetUrl);
    void sendPasswordChanged(String recipient);
}
