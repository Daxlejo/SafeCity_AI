package com.safecityai.backend.controller;

import com.safecityai.backend.dto.AdminLogDTO;
import com.safecityai.backend.dto.UserResponseDTO;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.AdminAction;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.service.AdminLogService;
import com.safecityai.backend.service.ReportService;
import com.safecityai.backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final ReportService reportService;
    private final AdminLogService adminLogService;

    public AdminController(UserService userService, ReportService reportService,
                           AdminLogService adminLogService) {
        this.userService = userService;
        this.reportService = reportService;
        this.adminLogService = adminLogService;
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
                                                       @RequestParam UserRole role,
                                                       Authentication auth) {
        UserResponseDTO result = userService.changeRole(id, role);

        // Auditoría: registrar cambio de rol
        User admin = userService.findByEmail(auth.getName());
        adminLogService.log(admin.getId(), admin.getEmail(),
                AdminAction.USER_ROLE_CHANGED, "User", id.toString(),
                "Nuevo rol: " + role.name());

        return ResponseEntity.ok(result);
    }

    // PUT /api/v1/admin/users/{id}/ban → alternar ban/unban
    @PutMapping("/users/{id}/ban")
    public ResponseEntity<UserResponseDTO> toggleBan(@PathVariable Long id,
                                                      Authentication auth) {
        UserResponseDTO result = userService.toggleBan(id);

        // Auditoría: registrar ban/unban
        User admin = userService.findByEmail(auth.getName());
        AdminAction action = Boolean.TRUE.equals(result.getActive())
                ? AdminAction.USER_UNBANNED : AdminAction.USER_BANNED;
        adminLogService.log(admin.getId(), admin.getEmail(),
                action, "User", id.toString(),
                "Estado activo: " + result.getActive());

        return ResponseEntity.ok(result);
    }

    // DELETE /api/v1/admin/users/{id} → eliminar usuario (hard delete)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id,
                                                           Authentication auth) {
        userService.deleteUser(id);

        // Auditoría: registrar eliminación de usuario
        User admin = userService.findByEmail(auth.getName());
        adminLogService.log(admin.getId(), admin.getEmail(),
                AdminAction.USER_DELETED, "User", id.toString(),
                "Eliminación permanente (hard delete)");

        return ResponseEntity.ok(Map.of("message", "Usuario eliminado", "userId", id));
    }

    // GET /api/v1/admin/reports → todos los reportes (incluyendo rechazados)
    @GetMapping("/reports")
    public ResponseEntity<Object> getAllReportsAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "reportDate") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, Sort.by(sortDirection, sort));
        return ResponseEntity.ok(reportService.getAllReportsIncludingRejected(pageable));
    }

    // PUT /api/v1/admin/reports/{id}/status?status=VERIFIED → moderar reporte
    @PutMapping("/reports/{id}/status")
    public ResponseEntity<Object> updateReportStatus(@PathVariable Long id,
                                                      @RequestParam ReportStatus status,
                                                      Authentication auth) {
        reportService.updateStatus(id, status);

        // Auditoría: registrar cambio de estado de reporte
        User admin = userService.findByEmail(auth.getName());
        adminLogService.log(admin.getId(), admin.getEmail(),
                AdminAction.REPORT_STATUS_CHANGED, "Report", id.toString(),
                "Nuevo estado: " + status.name());

        return ResponseEntity.ok(Map.of(
            "message", "Reporte actualizado",
            "reportId", id,
            "newStatus", status
        ));
    }

    // ═══════════════ AUDIT LOG ENDPOINTS ═══════════════

    // GET /api/v1/admin/logs → todos los logs paginados
    @GetMapping("/logs")
    public ResponseEntity<Page<AdminLogDTO>> getAllLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminLogService.getAllLogs(pageable));
    }

    // GET /api/v1/admin/logs/by-admin/{adminId} → logs de un admin específico
    @GetMapping("/logs/by-admin/{adminId}")
    public ResponseEntity<Page<AdminLogDTO>> getLogsByAdmin(
            @PathVariable Long adminId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminLogService.getLogsByAdmin(adminId, pageable));
    }

    // GET /api/v1/admin/logs/by-action?action=REPORT_DELETED → logs por tipo
    @GetMapping("/logs/by-action")
    public ResponseEntity<Page<AdminLogDTO>> getLogsByAction(
            @RequestParam AdminAction action,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminLogService.getLogsByAction(action, pageable));
    }
}
