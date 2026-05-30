package com.safecityai.backend.model.enums;

/**
 * Catálogo de acciones administrativas registrables para auditoría.
 * Cada constante representa una operación sensible que un administrador
 * puede realizar en la plataforma SafeCity AI.
 */
public enum AdminAction {

    // Acciones sobre reportes
    REPORT_STATUS_CHANGED,
    REPORT_DELETED,

    // Acciones sobre usuarios
    USER_ROLE_CHANGED,
    USER_BANNED,
    USER_UNBANNED,
    USER_DELETED,

    // Acciones sobre OSINT
    OSINT_CONFIG_UPDATED,
    OSINT_SCAN_TRIGGERED,
    OSINT_TOGGLED,

    // Acciones sobre zonas
    ZONE_CREATED,
    ZONE_UPDATED,
    ZONE_DELETED
}
