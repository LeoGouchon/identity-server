package com.leogouchon.identityserver.registration;

import com.leogouchon.identityserver.registration.dto.InvitationRequest;
import com.leogouchon.identityserver.registration.dto.InvitationResponse;
import com.leogouchon.identityserver.registration.dto.SignupRequest;
import com.leogouchon.identityserver.registration.dto.SignupResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {

        this.registration = registration;
    }

    @PostMapping("/v1/signup")
    public SignupResponse signup(@RequestBody SignupRequest request) {

        return registration.signup(request);
    }

    @PostMapping("/internal/invitations")
    public InvitationResponse createInvitation(
            @RequestHeader("X-Identity-Provisioning-Secret") String provided,
            @RequestBody InvitationRequest request
    ) {

        return registration.createInvitation(provided, request);
    }

    @GetMapping("/v1/invitations/{token}")
    public InvitationResponse invitation(@PathVariable String token) {

        return registration.invitation(token);
    }

    @PostMapping("/v1/invitations/{token}/claim")
    public InvitationResponse claim(@PathVariable String token, Authentication authentication) {

        return registration.claim(token, authentication);
    }
}
