package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** JSON que manda el TMS (balanza) con el peso del camión vacío. */
@Getter
@Setter
public class TareRequest {

    @Schema(description = "Peso del camión vacío, en kg", example = "8500")
    @NotNull(message = "es obligatoria")
    @Positive(message = "debe ser mayor a 0")
    // Mismo tope que el pesaje final: con una tara mayor la orden nunca podría finalizar, porque el
    // pesaje final no puede ser menor que la tara. También rechaza un número desbordado (1e400).
    @Max(value = 1000000, message = "no puede superar los 1000000 kg")
    private Double tare;
}
