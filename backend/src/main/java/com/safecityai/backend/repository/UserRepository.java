package com.safecityai.backend.repository;

import com.safecityai.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCedula(String cedula);

    boolean existsByEmail(String email);

    boolean existsByCedula(String cedula);

    Optional<User> findByResetToken(String resetToken);
}
