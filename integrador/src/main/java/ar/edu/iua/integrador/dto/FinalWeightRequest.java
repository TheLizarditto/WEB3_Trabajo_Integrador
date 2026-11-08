package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Peso bruto final informado por la balanza, en kg. */
@Getter
@Setter
public class FinalWeightRequest {
    @Schema(description = "Peso bruto final del camión, en kg", example = "23000")
    @NotNull
    @Positive
    @Max(1000000)
    private Double weight;

    @Size(max = 255)
    private String observation;
}
