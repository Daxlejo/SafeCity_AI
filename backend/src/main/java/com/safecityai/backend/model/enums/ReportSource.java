package com.safecityai.backend.model.enums;

public enum ReportSource {
    CITIZEN_TEXT,
    CITIZEN_VOICE,
    INSTITUTIONAL,
    SOCIAL_MEDIA,
    OSINT_AUTO   // Reporte creado automáticamente por el pipeline OSINT (con IA + geocoding)
}
