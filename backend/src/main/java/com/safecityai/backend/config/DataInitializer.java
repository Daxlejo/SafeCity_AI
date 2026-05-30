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

import java.util.Optional;

@Slf4j
@Configuration
public class DataInitializer {

    private static final String ADMIN_CEDULA = "00000000000";

    @Value("${app.admin.email:admin@safecity.ai}")
    private String adminEmail;

    @Value("${app.admin.password:Admin2026!}")
    private String adminPassword;

    @Value("${app.admin.name:Administrador SafeCity}")
    private String adminName;

    @Bean
    CommandLineRunner seedAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                log.info("[Init] Verificando cuenta admin con email: {}", adminEmail);

                // 1) Buscar por email configurado
                Optional<User> byEmail = userRepository.findByEmail(adminEmail);
                if (byEmail.isPresent()) {
                    User existing = byEmail.get();
                    // Asegurar que tenga rol ADMIN (por si se cambió manualmente)
                    if (existing.getRole() != UserRole.ADMIN) {
                        existing.setRole(UserRole.ADMIN);
                        existing.setTrustLevel(100.0);
                        userRepository.save(existing);
                        log.info("[Init] Usuario {} promovido a ADMIN", adminEmail);
                    } else {
                        log.info("[Init] Cuenta admin ya existe: {} (id={})", adminEmail, existing.getId());
                    }
                    return;
                }

                // 2) Buscar por cédula de admin (el email cambió vía env vars)
                Optional<User> byCedula = userRepository.findByCedula(ADMIN_CEDULA);
                if (byCedula.isPresent()) {
                    User existing = byCedula.get();
                    // Actualizar email y password al nuevo valor de env vars
                    existing.setEmail(adminEmail);
                    existing.setName(adminName);
                    existing.setPasswordHash(passwordEncoder.encode(adminPassword));
                    existing.setRole(UserRole.ADMIN);
                    existing.setTrustLevel(100.0);
                    existing.setActive(true);
                    userRepository.save(existing);
                    log.info("[Init] Cuenta admin actualizada con nuevo email: {}", adminEmail);
                    return;
                }

                // 3) No existe → crear nueva cuenta admin
                User admin = User.builder()
                        .name(adminName)
                        .email(adminEmail)
                        .cedula(ADMIN_CEDULA)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .role(UserRole.ADMIN)
                        .trustLevel(100.0)
                        .active(true)
                        .build();
                userRepository.save(admin);
                log.info("[Init] ✅ Cuenta admin creada exitosamente: {} (cédula: {})", adminEmail, ADMIN_CEDULA);

            } catch (Exception e) {
                log.error("[Init] ❌ ERROR creando cuenta admin: {}", e.getMessage(), e);
            }
        };
    }
}
