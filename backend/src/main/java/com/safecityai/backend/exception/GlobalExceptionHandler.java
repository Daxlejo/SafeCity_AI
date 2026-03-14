package com.safecityai.backend.exception;

import com.safecityai.backend.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Captura las excepciones lanzadas en CUALQUIER controller
 * y devuelve una respuesta JSON estandarizada con ErrorResponse.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 400 - BAD REQUEST
     * Se dispara cuando falla la validación de un @Valid en un controller.
     * Ejemplo: un campo @NotBlank llega vacío.
     * Devuelve la lista de errores de validación en "details".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Error de validación",
                "Uno o más campos no son válidos",
                details
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * 404 - NOT FOUND
     * Se dispara cuando se lanza ResourceNotFoundException.
     * Ejemplo: buscar un reporte con un ID que no existe.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Recurso no encontrado",
                ex.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * 500 - INTERNAL SERVER ERROR
     * Captura CUALQUIER otra excepción no manejada.
     * Actúa como red de seguridad para que nunca se exponga
     * un stack trace al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno del servidor",
                "Ha ocurrido un error inesperado. Por favor intente más tarde."
        );

        // En un entorno real, aquí logearías el error:
        // log.error("Error inesperado: ", ex);

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
