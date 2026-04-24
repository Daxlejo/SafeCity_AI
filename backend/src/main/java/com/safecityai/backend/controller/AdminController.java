package com.safecityai.backend.controller;

import com.safecityai.backend.dto.UserResponseDTO;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.service.ReportService;
import com.safecityai.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final ReportService reportService;

    public AdminController(UserService userService, ReportService reportService) {
        this.userService = userService;
        this.reportService = reportService;
    }

    // GET /api/v1/admin/dashboard → info básica del sistema
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(Map.of(
            "message", "SafeCity AI Admin Dashboard",
            "version", "1.0.0-sprint9",
            "status", "running"
        ));
    }

    // GET /api/v1/admin/users → listar todos los usuarios (paginado)
    @GetMapping("/users")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(pageable));
    }

    // GET /api/v1/admin/users/{id} → detalle de un usuario
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    // PUT /api/v1/admin/users/{id}/role?role=ADMIN → cambiar rol
    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserResponseDTO> changeRole(@PathVariable Long id,
                                                       @RequestParam UserRole role) {
        return ResponseEntity.ok(userService.changeRole(id, role));
    }

    // PUT /api/v1/admin/users/{id}/ban → alternar ban/unban
    @PutMapping("/users/{id}/ban")
    public ResponseEntity<UserResponseDTO> toggleBan(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleBan(id));
    }

    // DELETE /api/v1/admin/users/{id} → eliminar usuario (hard delete)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Usuario eliminado", "userId", id));
    }

    // PUT /api/v1/admin/reports/{id}/status?status=VERIFIED → moderar reporte
    @PutMapping("/reports/{id}/status")
    public ResponseEntity<Object> updateReportStatus(@PathVariable Long id,
                                                      @RequestParam ReportStatus status) {
        reportService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of(
            "message", "Reporte actualizado",
            "reportId", id,
            "newStatus", status
        ));
    }
}

