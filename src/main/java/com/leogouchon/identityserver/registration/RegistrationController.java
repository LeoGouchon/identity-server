package com.leogouchon.identityserver.registration;

import com.leogouchon.identityserver.registration.dto.InvitationRequest;
import com.leogouchon.identityserver.registration.dto.InvitationResponse;
import com.leogouchon.identityserver.registration.dto.SignupRequest;
import com.leogouchon.identityserver.registration.dto.SignupResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/v1/signup")
    public SignupResponse signup(@RequestBody SignupRequest request) {
        return registration.signup(request);
    }

    @PostMapping("/api/internal/invitations")
    public InvitationResponse createInvitation(@RequestHeader("X-Identity-Provisioning-Secret") String provided,
                                                @RequestBody InvitationRequest request) {
        return registration.createInvitation(provided, request);
    }
}
