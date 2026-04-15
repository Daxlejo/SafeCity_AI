package com.safecityai.backend.config;

import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String adminEmail = "admin@safecity.ai";
            if (userRepository.findByEmail(adminEmail).isEmpty()) {
                User admin = User.builder()
                        .name("Administrador SafeCity")
                        .email(adminEmail)
                        .cedula("00000000")
                        .passwordHash(passwordEncoder.encode("Admin2026!"))
                        .role(UserRole.ADMIN)
                        .trustLevel(100.0)
                        .build();
                userRepository.save(admin);
                System.out.println(">>> Admin account created: admin@safecity.ai / Admin2026!");
            }
        };
    }
}
