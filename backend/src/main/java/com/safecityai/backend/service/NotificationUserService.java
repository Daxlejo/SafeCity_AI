package com.safecityai.backend.service;

import com.safecityai.backend.dto.NotificationDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Notification;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationUserService {

    private final NotificationRepository notificationRepository;

    public NotificationUserService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // Todas las notificaciones de un usuario
    public List<NotificationDTO> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Solo no leidas
    public List<NotificationDTO> getUnread(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // Conteo de no leidas
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    // Marcar como leida
    @Transactional
    public NotificationDTO markAsRead(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificacion no encontrada"));
        n.setRead(true);
        return toDTO(notificationRepository.save(n));
    }

    // Crear notificacion para un usuario
    @Transactional
    public NotificationDTO createNotification(User user, Report report, String title, String message, String type) {
        Notification n = Notification.builder()
                .user(user)
                .report(report)
                .title(title)
                .message(message)
                .type(type)
                .build();
        return toDTO(notificationRepository.save(n));
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.getRead())
                .userId(n.getUser().getId())
                .reportId(n.getReport() != null ? n.getReport().getId() : null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
