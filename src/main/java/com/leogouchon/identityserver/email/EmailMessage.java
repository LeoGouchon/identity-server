package com.leogouchon.identityserver.email;

public record EmailMessage(String recipient, String subject, String html) {
}
