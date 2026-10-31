package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
    private Double tare;
}
