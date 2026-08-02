package com.leogouchon.identityserver.oauth2;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping
public class TokenController {
    private final OAuth2Service oauth2;

    public TokenController(OAuth2Service oauth2) {
        this.oauth2 = oauth2;
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        return oauth2.discovery();
    }

    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return oauth2.jwks();
    }

    @GetMapping("/oauth2/authorize")
    public void authorize(@RequestParam String response_type, @RequestParam String client_id,
                          @RequestParam String redirect_uri, @RequestParam(defaultValue = "openid") String scope,
                          @RequestParam String state, @RequestParam String nonce,
                          @RequestParam String code_challenge, @RequestParam(defaultValue = "S256") String code_challenge_method,
                          @RequestParam(required = false) String resource, Authentication authentication,
                          jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.sendRedirect(oauth2.authorize(response_type, client_id, redirect_uri, scope, state, nonce,
                code_challenge, code_challenge_method, resource, authentication));
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> token(@RequestParam String grant_type, @RequestParam String client_id,
                                     @RequestParam(required = false) String code,
                                     @RequestParam(required = false) String redirect_uri,
                                     @RequestParam(required = false) String code_verifier,
                                     @RequestParam(required = false) String refresh_token) {
        return oauth2.token(grant_type, client_id, code, redirect_uri, code_verifier, refresh_token);
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userinfo(Authentication authentication) {
        return oauth2.userinfo(authentication);
    }

    @GetMapping("/connect/logout")
    public void logout(@RequestParam(required = false) String post_logout_redirect_uri,
                       jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.sendRedirect(oauth2.logout(post_logout_redirect_uri));
    }

    @PostMapping("/oauth2/revoke")
    public void revoke(@RequestParam String token) {
        oauth2.revoke(token);
    }
}
