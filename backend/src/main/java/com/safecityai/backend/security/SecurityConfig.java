package com.safecityai.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos (sin token)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**", "/ws-sockjs/**").permitAll()
                        // GET de reportes es público (el mapa lo necesita sin login)
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/**").permitAll()

                        // GET de zonas y stats son publicos (para mapa y dashboard)
                        .requestMatchers(HttpMethod.GET, "/api/v1/zones/**").permitAll()
                        .requestMatchers("/api/v1/zones/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/stats/**").permitAll()

                        // OSINT: publico para permitir busquedas y triggers automaticos
                        .requestMatchers("/api/v1/osint/**").permitAll()

                        // Fotos: GET es publico para que se vean en el frontend
                        .requestMatchers(HttpMethod.GET, "/api/v1/uploads/**").permitAll()

                        // Todo lo demás requiere autenticación
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
