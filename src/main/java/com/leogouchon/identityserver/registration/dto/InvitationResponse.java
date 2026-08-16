package com.leogouchon.identityserver.registration.dto;

public record InvitationResponse(String token, String clientId, String applicationName, String invitationUrl,
                                 String applicationUrl) {
}
