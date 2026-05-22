package com.safecityai.backend.controller;

import com.safecityai.backend.dto.NotificationDTO;
import com.safecityai.backend.model.User;
import com.safecityai.backend.service.NotificationUserService;
import com.safecityai.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationUserService notifService;
    private final UserService userService;

    public NotificationController(NotificationUserService notifService, UserService userService) {
        this.notifService = notifService;
        this.userService = userService;
    }

    // GET /api/v1/notifications → todas mis notificaciones
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(notifService.getUserNotifications(user.getId()));
    }

    // GET /api/v1/notifications/unread → solo las no leidas
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnread(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        return ResponseEntity.ok(notifService.getUnread(user.getId()));
    }

    // GET /api/v1/notifications/count → conteo de no leidas (para el badge)
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUnread(Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        long count = notifService.countUnread(user.getId());
        return ResponseEntity.ok(Map.of("unread", count));
    }

    // PUT /api/v1/notifications/{id}/read → marcar como leida
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notifService.markAsRead(id));
    }
}
