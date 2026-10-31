package ar.edu.iua.integrador.controllers;

import ar.edu.iua.integrador.business.exception.BusinessException;
import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.util.StandartResponse;
import ar.edu.iua.integrador.util.StandartResponseBusiness;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Traduce las excepciones que salen de cualquier controller a una respuesta HTTP con
 * StandartResponse. Así los controllers no tienen try/catch: solo llaman al negocio.
 */
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    /** Lo pedido no existe: 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<StandartResponse> handleNotFound(NotFoundException ex) {
        return StandartResponseBusiness.build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Se quiso crear algo que ya existe: 409. */
    @ExceptionHandler(FoundException.class)
    public ResponseEntity<StandartResponse> handleFound(FoundException ex) {
        return StandartResponseBusiness.build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** La operación no corresponde al estado de la orden: 409. */
    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<StandartResponse> handleInvalidState(InvalidStateException ex) {
        return StandartResponseBusiness.build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** La contraseña de activación no corresponde: 403. */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<StandartResponse> handleInvalidPassword(InvalidPasswordException ex) {
        return StandartResponseBusiness.build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Error inesperado del negocio: 500. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StandartResponse> handleBusiness(BusinessException ex) {
        // El detalle real puede tener datos internos (SQL, nombres de tablas): va al log, no al cliente.
        log.error(ex.getMessage(), ex);
        return StandartResponseBusiness.build(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    /** El JSON llegó bien formado pero no cumple las validaciones del DTO (@NotNull, @Positive...): 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandartResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Juntamos todos los campos con error en un solo mensaje, así SAP los corrige de una vez.
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
        return StandartResponseBusiness.build(HttpStatus.BAD_REQUEST, message);
    }

    /** El cuerpo no es un JSON válido o tiene un tipo incorrecto (texto donde va un número): 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandartResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return StandartResponseBusiness.build(
                HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido");
    }

    /** Un parámetro de la URL no tiene el tipo esperado (por ejemplo /orders/abc): 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandartResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        // Hace falta porque es una RuntimeException: sin este handler caería en el 500 de abajo.
        return StandartResponseBusiness.build(
                HttpStatus.BAD_REQUEST, "Valor inválido para '" + ex.getName() + "'");
    }

    /** Cualquier otro error no previsto (un bug, la base caída...): 500 con el mismo formato. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<StandartResponse> handleUnexpected(RuntimeException ex) {
        // Spring elige el handler más específico, así que este solo atiende lo que no matcheó arriba.
        // Los 404 de URL inexistente y los 405 no pasan por acá: son ServletException, no RuntimeException.
        log.error(ex.getMessage(), ex);
        return StandartResponseBusiness.build(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }
}
