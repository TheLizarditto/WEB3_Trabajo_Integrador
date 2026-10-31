package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/** Se pidió algo que no existe (por ejemplo, una orden con un id inválido). Se devuelve como 404. */
public class NotFoundException extends Exception {

    // Con @Builder se crea así: NotFoundException.builder().message("...").build().
    // La causa (ex) es opcional: se pasa cuando envolvemos otra excepción para no perder el detalle.
    @Builder
    public NotFoundException(String message, Throwable ex) {
        super(message, ex);
    }
}
