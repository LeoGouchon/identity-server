package com.leogouchon.identityserver.security;

import com.leogouchon.identityserver.config.IdentityProperties;
import com.leogouchon.identityserver.user.IdentityUser;
import com.leogouchon.identityserver.user.IdentityUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    JwtDecoder jwtDecoder(TokenService tokenService) {
        return NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) tokenService.keyPair().getPublic()).build();
    }

    @Bean
    UserDetailsService userDetailsService(IdentityUserRepository users) {
        return username -> users.findByEmailIgnoreCase(username)
                .filter(IdentityUser::isEnabled)
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${identity.auth-frontend-url:http://localhost:5180}") String authFrontendUrl) throws Exception {
        http.cors(cors -> {})
                .csrf(csrf -> csrf.ignoringRequestMatchers("/login", "/oauth2/token", "/oauth2/revoke", "/api/internal/**", "/api/v1/password/**", "/api/v1/invitations/*/claim"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/**", "/oauth2/jwks", "/oauth2/token", "/oauth2/revoke", "/error", "/api/internal/**").permitAll()
                        .requestMatchers("/oauth2/authorize", "/userinfo", "/connect/logout", "/api/v1/invitations/*/claim").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage(authFrontendUrl + "/login")
                        .loginProcessingUrl("/login")
                        .failureUrl(authFrontendUrl + "/login?error=authentication_failed")
                        .successHandler((request, response, authentication) -> {
                            String invitation = request.getParameter("invitation");
                            if (invitation != null && !invitation.isBlank()) {
                                response.sendRedirect(authFrontendUrl + "/invitation/complete?invitation="
                                        + java.net.URLEncoder.encode(invitation, java.nio.charset.StandardCharsets.UTF_8));
                            } else {
                                new SavedRequestAwareAuthenticationSuccessHandler().onAuthenticationSuccess(request, response, authentication);
                            }
                        })
                        .permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
