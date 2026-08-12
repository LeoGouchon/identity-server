package com.leogouchon.identityserver.password_reset;


import com.leogouchon.identityserver.password_reset.dto.ForgotPasswordRequest;
import com.leogouchon.identityserver.password_reset.dto.PasswordResetResponse;
import com.leogouchon.identityserver.password_reset.dto.ResetPasswordRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/password")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/forgot")
    public PasswordResetResponse forgot(@RequestBody ForgotPasswordRequest request) { return service.requestReset(request); }

    @PostMapping("/reset")
    public PasswordResetResponse reset(@RequestBody ResetPasswordRequest request) { return service.resetPassword(request); }
}
