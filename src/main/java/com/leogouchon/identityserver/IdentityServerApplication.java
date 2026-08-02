package com.leogouchon.identityserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.leogouchon.identityserver")
@EnableScheduling
public class IdentityServerApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServerApplication.class, args);
    }

    private final List<String> corsAllowedOrigins;

    public IdentityServerApplication(@Value("${identity.cors.allowed-origins}") String corsAllowedOrigins) {
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split("\\|"))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsAllowedOrigins.toArray(String[]::new))
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
