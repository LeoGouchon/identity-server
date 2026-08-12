package com.leogouchon.identityserver.email;

public interface EmailClient {
    void send(EmailMessage message);
}
