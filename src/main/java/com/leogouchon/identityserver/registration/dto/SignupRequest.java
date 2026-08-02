package com.leogouchon.identityserver.registration.dto;

public record SignupRequest(String email, String password, String invitationToken) {
}
