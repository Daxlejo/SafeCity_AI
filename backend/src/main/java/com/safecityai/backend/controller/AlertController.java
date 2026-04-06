package com.safecityai.backend.controller;

import com.safecityai.backend.dto.AlertPreferenceDTO;
import com.safecityai.backend.model.User;
import com.safecityai.backend.service.AlertService;
import com.safecityai.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts/preferences")
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    public AlertController(AlertService alertService, UserService userService) {
        this.alertService = alertService;
        this.userService = userService;
    }

    // GET /api/v1/alerts/preferences → las preferencias del usuario autenticado
    @GetMapping
    public ResponseEntity<List<AlertPreferenceDTO>> getMyPreferences(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(alertService.getPreferences(user.getId()));
    }

    // GET /api/v1/alerts/preferences/active → solo las activas
    @GetMapping("/active")
    public ResponseEntity<List<AlertPreferenceDTO>> getActivePreferences(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(alertService.getActivePreferences(user.getId()));
    }

    // POST /api/v1/alerts/preferences → crear/actualizar preferencia
    @PostMapping
    public ResponseEntity<AlertPreferenceDTO> savePreference(Authentication auth,
                                                              @Valid @RequestBody AlertPreferenceDTO dto) {
        User user = userService.findByEmail(auth.getName());
        return new ResponseEntity<>(alertService.savePreference(user.getId(), user, dto), HttpStatus.CREATED);
    }

    // DELETE /api/v1/alerts/preferences/{id} → eliminar preferencia
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePreference(@PathVariable Long id) {
        alertService.deletePreference(id);
        return ResponseEntity.noContent().build();
    }
}
