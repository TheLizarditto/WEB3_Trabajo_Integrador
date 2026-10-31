package ar.edu.iua.integrador.business.exception;

import lombok.Builder;

/** Error inesperado en la lógica de negocio. El controller lo devuelve como 500. */
public class BusinessException extends Exception {

    // Con @Builder se crea así: BusinessException.builder().message("...").build().
    // La causa (ex) es opcional: se pasa cuando envolvemos otra excepción para no perder el detalle.
    @Builder
    public BusinessException(String message, Throwable ex) {
        super(message, ex);
    }
}
