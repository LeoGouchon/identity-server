package com.leogouchon.identityserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "identity")
public class IdentityProperties {
    private String authFrontendUrl = "http://localhost:5180";
    private List<OAuthClient> oauthClients = List.of();
    private List<String> allowedBackends = List.of();
    private List<String> scopes = List.of();
    private Cors cors = new Cors();

    @Setter
    @Getter
    public static class OAuthClient {
        private String clientId;
        private List<String> redirectUris = List.of();

    }

    @Setter
    @Getter
    public static class Cors {
        private List<String> allowedOrigins = List.of();
    }
}
