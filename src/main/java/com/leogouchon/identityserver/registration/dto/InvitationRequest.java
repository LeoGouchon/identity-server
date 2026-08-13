package com.leogouchon.identityserver.registration.dto;

import java.util.UUID;

public record InvitationRequest(String clientId, UUID playerId) {
}
