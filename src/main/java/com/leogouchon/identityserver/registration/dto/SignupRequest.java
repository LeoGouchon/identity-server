package com.leogouchon.identityserver.registration.dto;

public record SignupRequest(String email, String password, String invitationToken,
                            String firstName, String lastName) {
    public SignupRequest(String email, String password, String invitationToken) {
        this(email, password, invitationToken, null, null);
    }
}
