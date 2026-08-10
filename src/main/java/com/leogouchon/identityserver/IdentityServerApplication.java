package com.leogouchon.identityserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.leogouchon.identityserver.config.IdentityProperties;

import java.util.List;

@SpringBootApplication(scanBasePackages = "com.leogouchon.identityserver")
@EnableConfigurationProperties(IdentityProperties.class)
@EnableScheduling
public class IdentityServerApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServerApplication.class, args);
    }

    private final List<String> corsAllowedOrigins;

    public IdentityServerApplication(IdentityProperties identityProperties) {
        this.corsAllowedOrigins = identityProperties.getCors().getAllowedOrigins();
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
