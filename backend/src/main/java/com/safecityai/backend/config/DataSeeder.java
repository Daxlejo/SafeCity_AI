package com.safecityai.backend.config;

import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner loadAdminData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByEmail("admin@safecity.com")) {
                User admin = User.builder()
                        .name("Super Admin")
                        .email("admin@safecity.com")
                        .cedula("0000000000") // Cédula ficticia de 10 dígitos para el admin
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .role(UserRole.ADMIN)
                        .trustLevel(100.0)
                        .build();
                userRepository.save(admin);
                System.out.println("✅ IMPORTANTE: Usuario ADMIN creado por defecto -> Email: admin@safecity.com | Password: admin123");
            }
        };
    }
}
