package com.safecityai.backend.exception;

/**
 * Excepción para cuando un usuario excede el límite de reportes por hora.
 * El GlobalExceptionHandler la captura y devuelve un HTTP 429 (Too Many Requests).
 *
 * Los límites varían según el TrueScore del usuario:
 * - TrustLevel < 65  → 3 reportes/hora
 * - TrustLevel 65-74 → 4 reportes/hora
 * - TrustLevel >= 75 → 5 reportes/hora
 * - Administradores  → sin límite
 */
public class RateLimitExceededException extends RuntimeException {

    private final int limit;
    private final int used;
    private final String resetsAt;

    public RateLimitExceededException(int limit, int used, String resetsAt) {
        super(String.format(
                "Has alcanzado el límite de %d reportes por hora. Podrás enviar más a las %s.",
                limit, resetsAt));
        this.limit = limit;
        this.used = used;
        this.resetsAt = resetsAt;
    }

    public int getLimit() {
        return limit;
    }

    public int getUsed() {
        return used;
    }

    public String getResetsAt() {
        return resetsAt;
    }
}
