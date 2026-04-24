package com.safecityai.backend.controller;

import com.safecityai.backend.dto.AlertPreferenceDTO;
import com.safecityai.backend.model.User;
import com.safecityai.backend.service.AlertService;
import com.safecityai.backend.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts/preferences")
@Tag(name = "Alerts Preferences", description = "Gestión de preferencias de notificaciones y alertas por tipo de incidente para los usuarios")
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    public AlertController(AlertService alertService, UserService userService) {
        this.alertService = alertService;
        this.userService = userService;
    }

    // GET /api/v1/alerts/preferences → las preferencias del usuario autenticado
    @GetMapping
    @Operation(summary = "Obtener todas las preferencias", description = "Retorna todas las preferencias de alerta configuradas para el usuario autenticado (tanto activas como pausadas).")
    @ApiResponse(responseCode = "200", description = "Preferencias obtenidas exitosamente")
    public ResponseEntity<List<AlertPreferenceDTO>> getMyPreferences(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(alertService.getPreferences(user.getId()));
    }

    // GET /api/v1/alerts/preferences/active → solo las activas
    @GetMapping("/active")
    @Operation(summary = "Obtener preferencias activas", description = "Retorna únicamente las preferencias de alerta que están actualmente activadas y habilitadas para notificar.")
    @ApiResponse(responseCode = "200", description = "Preferencias activas obtenidas exitosamente")
    public ResponseEntity<List<AlertPreferenceDTO>> getActivePreferences(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(alertService.getActivePreferences(user.getId()));
    }

    // POST /api/v1/alerts/preferences → crear/actualizar preferencia
    @PostMapping
    @Operation(summary = "Crear o actualizar preferencia", description = "Crea una nueva suscripción de alerta para un tipo de incidente o actualiza una existente (por ejemplo, para pausarla/activarla).")
    @ApiResponse(responseCode = "201", description = "Preferencia guardada exitosamente")
    public ResponseEntity<AlertPreferenceDTO> savePreference(Authentication auth,
                                                              @Valid @RequestBody AlertPreferenceDTO dto) {
        User user = userService.findByEmail(auth.getName());
        return new ResponseEntity<>(alertService.savePreference(user.getId(), user, dto), HttpStatus.CREATED);
    }

    // DELETE /api/v1/alerts/preferences/{id} → eliminar preferencia
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar preferencia", description = "Elimina permanentemente una preferencia de alerta del usuario.")
    @ApiResponse(responseCode = "204", description = "Preferencia eliminada exitosamente")
    @ApiResponse(responseCode = "404", description = "Preferencia no encontrada")
    public ResponseEntity<Void> deletePreference(@PathVariable Long id) {
        alertService.deletePreference(id);
        return ResponseEntity.noContent().build();
    }
}
