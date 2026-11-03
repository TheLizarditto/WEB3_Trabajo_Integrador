package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/** Dato numérico inválido para procesar una lectura; se devuelve como 400. */
public class InvalidDataException extends Exception {
    @Builder
    public InvalidDataException(String message, Throwable ex) {
        super(message, ex);
    }
}
