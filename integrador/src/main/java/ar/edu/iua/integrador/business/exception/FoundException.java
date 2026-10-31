package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/** Se quiso crear algo que ya existe (por ejemplo, un número de orden repetido). Se devuelve como 409. */
public class FoundException extends Exception {

    // Con @Builder se crea así: FoundException.builder().message("...").build().
    // La causa (ex) es opcional: se pasa cuando envolvemos otra excepción para no perder el detalle.
    @Builder
    public FoundException(String message, Throwable ex) {
        super(message, ex);
    }
}
