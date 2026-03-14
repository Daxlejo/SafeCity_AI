package com.safecityai.backend.exception;

/**
 * Excepción personalizada para cuando un recurso no se encuentra.
 * Al lanzar esta excepción, el GlobalExceptionHandler la captura
 * y devuelve automáticamente un 404 con el mensaje correspondiente.
 *
 * Uso: throw new ResourceNotFoundException("Reporte", "id", 123);
 * Resultado: "Reporte no encontrado con id: 123"
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s no encontrado con %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}
