package com.safecityai.backend.model.enums;

/**
 * Nivel de confianza asignado por la IA a un reporte.
 * Se calcula en base al historial del usuario + contenido del reporte.
 */
public enum TrustLevel {
    UNTRUSTED,   // < 20% confianza
    LOW,         // 20-40%
    MODERATE,    // 40-60%
    HIGH,        // 60-80%
    VERIFIED     // > 80% confianza
}
