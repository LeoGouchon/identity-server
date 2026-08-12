package com.leogouchon.identityserver.security;

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
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/login", "/oauth2/token", "/oauth2/revoke", "/api/internal/**", "/api/v1/password/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/**", "/oauth2/jwks", "/oauth2/token", "/oauth2/revoke", "/error", "/api/internal/**").permitAll()
                        .requestMatchers("/oauth2/authorize", "/userinfo", "/connect/logout").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage(authFrontendUrl + "/login")
                        .loginProcessingUrl("/login")
                        .failureUrl(authFrontendUrl + "/login?error=authentication_failed")
                        .permitAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }
}
