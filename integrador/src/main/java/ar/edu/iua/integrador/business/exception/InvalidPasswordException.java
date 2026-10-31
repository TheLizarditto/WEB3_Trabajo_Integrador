package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/**
 * La contraseña de activación no corresponde a la orden. Se devuelve como 403: el 401 queda para
 * cuando falte la autenticación (JWT), que es otro problema.
 */
public class InvalidPasswordException extends Exception {

    // Con @Builder se crea así: InvalidPasswordException.builder().message("...").build().
    // La causa (ex) es opcional: se pasa cuando envolvemos otra excepción para no perder el detalle.
    @Builder
    public InvalidPasswordException(String message, Throwable ex) {
        super(message, ex);
    }
}
