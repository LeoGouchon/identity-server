package com.leogouchon.identityserver.oauth2;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

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
    public void authorize(@RequestParam("response_type") String responseType,
                          @RequestParam("client_id") String clientId,
                          @RequestParam("redirect_uri") String redirectUri,
                          @RequestParam(defaultValue = "openid") String scope,
                          @RequestParam String state, @RequestParam String nonce,
                          @RequestParam("code_challenge") String codeChallenge,
                          @RequestParam(name = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
                          @RequestParam(required = false) String resource, Authentication authentication,
                          jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.sendRedirect(oauth2.authorize(responseType, clientId, redirectUri, scope, state, nonce,
                codeChallenge, codeChallengeMethod, resource, authentication));
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> token(@RequestParam("grant_type") String grantType,
                                     @RequestParam("client_id") String clientId,
                                     @RequestParam(required = false) String code,
                                     @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                     @RequestParam(name = "code_verifier", required = false) String codeVerifier,
                                     @RequestParam(name = "refresh_token", required = false) String refreshToken) {
        return oauth2.token(grantType, clientId, code, redirectUri, codeVerifier, refreshToken);
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userinfo(Authentication authentication) {
        return oauth2.userinfo(authentication);
    }

    @GetMapping("/connect/logout")
    public void logout(@RequestParam(name = "client_id", required = false) String clientId,
                       @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
                       @RequestParam(name = "state", required = false) String state,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response,
                       Authentication authentication) throws IOException {
        String redirect = oauth2.logout(clientId, postLogoutRedirectUri, state);
        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);
        response.sendRedirect(redirect);
    }

    @PostMapping("/oauth2/revoke")
    public void revoke(@RequestParam String token) {
        oauth2.revoke(token);
    }
}
