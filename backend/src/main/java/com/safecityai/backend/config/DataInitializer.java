package com.safecityai.backend.config;

import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
public class DataInitializer {

    @Value("${app.admin.email:admin@safecity.ai}")
    private String adminEmail;

    @Value("${app.admin.password:Admin2026!}")
    private String adminPassword;

    @Value("${app.admin.name:Administrador SafeCity}")
    private String adminName;

    @Bean
    CommandLineRunner seedAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail(adminEmail).isEmpty()) {
                User admin = User.builder()
                        .name(adminName)
                        .email(adminEmail)
                        .cedula("00000000")
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .role(UserRole.ADMIN)
                        .trustLevel(100.0)
                        .build();
                userRepository.save(admin);
                log.info("[Init] Cuenta admin creada: {}", adminEmail);
            } else {
                log.debug("[Init] Cuenta admin ya existe, omitiendo seed.");
            }
        };
    }
}
