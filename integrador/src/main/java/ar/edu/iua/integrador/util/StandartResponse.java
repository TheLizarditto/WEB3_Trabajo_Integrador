package ar.edu.iua.integrador.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** Cuerpo JSON que devolvemos cuando algo sale mal, igual para todos los errores de la API. */
@Getter
@AllArgsConstructor
public class StandartResponse {
    private int code;
    private String error;
    private String message;
    private Instant timestamp;
}
