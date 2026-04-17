package com.safecityai.backend.service;

import com.safecityai.backend.dto.AuthResponseDTO;
import com.safecityai.backend.dto.UserLoginDTO;
import com.safecityai.backend.dto.UserRegisterDTO;
import com.safecityai.backend.dto.UserResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.repository.UserRepository;
import com.safecityai.backend.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Registrar nuevo usuario
    @Transactional
    public AuthResponseDTO register(UserRegisterDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }
        if (userRepository.existsByCedula(dto.getCedula())) {
            throw new IllegalArgumentException("La cédula ya está registrada");
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .cedula(dto.getCedula())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(UserRole.CITIZEN) // Fija explícitamente el rol CITIZEN
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getEmail(), saved.getRole().name());

        return AuthResponseDTO.of(token, toResponseDTO(saved));
    }

    // Login: Acepta email o cedula + contraseña
    public AuthResponseDTO login(UserLoginDTO dto) {
        String identifier = dto.getIdentifier().trim();

        User user;
        if (identifier.contains("@")) {
            user = userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));
        } else {
            user = userRepository.findByCedula(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        // Verificar si el usuario está baneado
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("Tu cuenta ha sido suspendida. Contacta al administrador.");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponseDTO.of(token, toResponseDTO(user));
    }

    // Buscar por ID
    public UserResponseDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con id " + id + " no encontrado"));
        return toResponseDTO(user);
    }

    // Buscar por email
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    // Listar todos paginados
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponseDTO);
    }

    // Actualizar perfil
    @Transactional
    public UserResponseDTO updateProfile(Long id, UserRegisterDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con id " + id + " no encontrado"));

        if (dto.getName() != null)
            user.setName(dto.getName());
        if (dto.getEmail() != null)
            user.setEmail(dto.getEmail());
        if (dto.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        return toResponseDTO(userRepository.save(user));
    }

    // ═══════════════ ADMIN ACTIONS ═══════════════

    // Cambiar rol de un usuario
    // Patrón RBAC (Role-Based Access Control):
    // Los permisos se derivan del rol, no del usuario individual
    @Transactional
    public UserResponseDTO changeRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con id " + userId + " no encontrado"));
        log.info("Cambiando rol de usuario {} de {} a {}", userId, user.getRole(), newRole);
        user.setRole(newRole);
        return toResponseDTO(userRepository.save(user));
    }

    // Ban/Unban de un usuario (Soft Delete pattern)
    // No eliminamos el usuario, solo lo desactivamos
    // Esto preserva la integridad referencial con sus reportes
    @Transactional
    public UserResponseDTO toggleBan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario con id " + userId + " no encontrado"));
        boolean newStatus = !Boolean.TRUE.equals(user.getActive());
        user.setActive(newStatus);
        log.info("Usuario {} {}", userId, newStatus ? "desbaneado" : "baneado");
        return toResponseDTO(userRepository.save(user));
    }

    // Eliminar usuario (Hard Delete)
    // ¿Cuándo usar Hard vs Soft Delete?
    // - Soft Delete (ban): preserva historial, reversible
    // - Hard Delete: cuando el usuario lo solicita (GDPR/ley de datos)
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario con id " + userId + " no encontrado");
        }
        log.warn("Eliminando usuario {} permanentemente", userId);
        userRepository.deleteById(userId);
    }

    // Helper: convierte entidad a DTO
    private UserResponseDTO toResponseDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .cedula(user.getCedula())
                .role(user.getRole())
                .trustLevel(user.getTrustLevel())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
