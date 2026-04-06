package com.safecityai.backend.model;

import com.safecityai.backend.model.enums.IncidentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alert_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType incidentType;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;
}
