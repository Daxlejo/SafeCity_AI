package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el ranking de zonas peligrosas semanal.
 *
 * ¿Por qué un DTO separado y no un Map?
 * ──────────────────────────────────────────
 * Un Map<String, Long> solo puede representar clave→valor.
 * Pero necesitamos MÚLTIPLES campos por zona:
 *   - nombre de la zona (o coordenada)
 *   - cantidad de incidentes
 *   - tipo más común en esa zona
 *   - nivel de riesgo calculado
 *
 * Esto es el principio de ENCAPSULACIÓN (OOP):
 * agrupamos datos relacionados en un solo objeto con nombre
 * semántico, en vez de pasar datos sueltos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DangerousZoneDTO {

    // Coordenadas representadas como "lat, lng" (aproximadas al área)
    private String areaName;

    // Cantidad de incidentes en los últimos N días
    private long incidentCount;

    // Tipo de incidente más frecuente en esa zona
    private String mostCommonType;

    // Nivel de riesgo: HIGH, MEDIUM, LOW
    // Esto es un ejemplo del patrón STRATEGY: la regla
    // para definir riesgo cambia según el conteo
    private String riskLevel;

    // Posición en el ranking (1 = más peligrosa)
    private int rank;
}
