package com.safecityai.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Configuración global del módulo OSINT.
 * Solo existe una fila (configuración singleton) en la tabla 'osint_config'.
 * Permite activar/desactivar el scheduler, ajustar intervalo de escaneo,
 * definir keywords de búsqueda y URLs prioritarias desde el panel admin.
 */
@Entity
@Table(name = "osint_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsintConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Activa o desactiva el scheduler automático de OSINT.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Intervalo en minutos entre cada ejecución del scan programado.
     */
    @Column(name = "interval_minutes", nullable = false)
    @Builder.Default
    private int intervalMinutes = 60;

    /**
     * Keywords de búsqueda almacenadas como JSON string.
     * Ejemplo: ["robo", "accidente", "hurto", "homicidio"]
     */
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private String keywords = "[\"robo\",\"accidente\",\"hurto\",\"homicidio\",\"choque\",\"atraco\"]";

    /**
     * URLs de fuentes prioritarias almacenadas como JSON string.
     * Ejemplo: ["Pasto Denuncias", "Nariño Noticias La Original"]
     */
    @Column(name = "priority_urls", columnDefinition = "TEXT")
    @Builder.Default
    private String priorityUrls = "[\"Pasto Denuncias\",\"Nariño Noticias La Original\",\"La Voz Del pueblo Noticias Nariño\"]";

    /**
     * Ciudad por defecto para las búsquedas OSINT.
     */
    @Column(name = "default_city", length = 100)
    @Builder.Default
    private String defaultCity = "Pasto";

    /**
     * Número máximo de ítems a procesar por ejecución del scan.
     */
    @Column(name = "max_items_per_execution")
    @Builder.Default
    private int maxItemsPerExecution = 10;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
