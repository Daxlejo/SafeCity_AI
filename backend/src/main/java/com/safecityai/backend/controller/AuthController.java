package com.safecityai.backend.controller;

import com.safecityai.backend.dto.AuthResponseDTO;
import com.safecityai.backend.dto.UserLoginDTO;
import com.safecityai.backend.dto.UserRegisterDTO;
import com.safecityai.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // POST /api/v1/auth/register → 201 Created
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody UserRegisterDTO dto) {
        return new ResponseEntity<>(userService.register(dto), HttpStatus.CREATED);
    }

    // POST /api/v1/auth/login → 200 OK
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody UserLoginDTO dto) {
        return ResponseEntity.ok(userService.login(dto));
    }
}
