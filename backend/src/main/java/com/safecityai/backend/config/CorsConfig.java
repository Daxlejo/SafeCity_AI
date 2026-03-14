package com.safecityai.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes permitidos en desarrollo
        config.addAllowedOrigin("http://localhost:3000");   // React (Create React App)
        config.addAllowedOrigin("http://localhost:5173");   // Vite (React/Vue)

        // Permite todos los métodos HTTP (GET, POST, PUT, DELETE, PATCH, OPTIONS, etc.)
        config.addAllowedMethod("*");

        // Headers permitidos
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");

        // Permitir cookies y autenticación
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }
}
