package com.leogouchon.identityserver.password_reset.dto;

public record ResetPasswordRequest(String email, String token, String password) {}
