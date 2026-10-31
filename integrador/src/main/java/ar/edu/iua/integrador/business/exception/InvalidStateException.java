package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/**
 * La operación no corresponde al estado actual de la orden (por ejemplo, registrar la tara de una
 * orden que ya la tiene). Se devuelve como 409.
 */
public class InvalidStateException extends Exception {

    // Con @Builder se crea así: InvalidStateException.builder().message("...").build().
    // La causa (ex) es opcional: se pasa cuando envolvemos otra excepción para no perder el detalle.
    @Builder
    public InvalidStateException(String message, Throwable ex) {
        super(message, ex);
    }
}
