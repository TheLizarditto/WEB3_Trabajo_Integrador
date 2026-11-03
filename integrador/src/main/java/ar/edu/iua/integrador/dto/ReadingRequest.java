package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Lectura enviada por el surtidor. La densidad se expresa en g/cm3. */
@Getter
@Setter
public class ReadingRequest {

    @Schema(description = "Masa acumulada cargada, en kg", example = "1200.0")
    @NotNull(message = "es obligatoria")
    private Double mass;

    @Schema(description = "Densidad del producto, en g/cm3; rango permitido de 0 a 1", example = "0.55")
    @NotNull(message = "es obligatoria")
    @DecimalMin(value = "0.0", message = "debe ser mayor o igual a 0 g/cm3")
    @DecimalMax(value = "1.0", message = "debe ser menor o igual a 1 g/cm3")
    private Double density;

    @Schema(description = "Temperatura del producto, en grados Celsius", example = "24.5")
    @NotNull(message = "es obligatoria")
    private Double temperature;

    @Schema(description = "Caudal, en kg/h", example = "1800.0")
    @NotNull(message = "es obligatorio")
    private Double flow;
}
