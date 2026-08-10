package com.leogouchon.identityserver.registration.dto;

import java.util.UUID;

public record SignupResponse(UUID id, String email) {
}
