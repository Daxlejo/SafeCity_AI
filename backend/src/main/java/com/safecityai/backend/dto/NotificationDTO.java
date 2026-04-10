package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    private String title;
    private String message;
    private String type;        // ALERT, INFO, WARNING
    private Boolean read;
    private Long userId;
    private Long reportId;
    private LocalDateTime createdAt;
}
