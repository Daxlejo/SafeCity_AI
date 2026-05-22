package com.safecityai.backend.model.enums;

/**
 * Estado de visibilidad de un artículo de noticias OSINT.
 * Los admins pueden controlar qué artículos ve el público.
 */
public enum OsintArticleStatus {
    /** Visible al público en el feed de noticias */
    PUBLISHED,
    /** Oculto al público pero conservado en BD para revisión del admin */
    HIDDEN
}
