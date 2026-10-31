package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Respuesta a una activación correcta: el preset que el surtidor necesita para cargar. */
@Getter
@AllArgsConstructor
public class ActivationResponse {

    @Schema(example = "501")
    private long orderNumber;

    @Schema(description = "Kilos a cargar", example = "15000")
    private double preset;
}
