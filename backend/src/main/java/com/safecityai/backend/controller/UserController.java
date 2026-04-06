package com.safecityai.backend.controller;

import com.safecityai.backend.dto.UserRegisterDTO;
import com.safecityai.backend.dto.UserResponseDTO;
import com.safecityai.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/v1/users → 200 OK (paginado)
    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    // GET /api/v1/users/{id} → 200 OK
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    // PUT /api/v1/users/{id} → 200 OK
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id,
                                                   @Valid @RequestBody UserRegisterDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(id, dto));
    }
}
