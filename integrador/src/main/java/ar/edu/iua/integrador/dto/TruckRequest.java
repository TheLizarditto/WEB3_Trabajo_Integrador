package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Datos del camión que vienen dentro de la orden de SAP. */
@Getter
@Setter
public class TruckRequest {

    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String externalCode;

    // Formato viejo (AAA123) o Mercosur (AA123BB). Se valida estricto en vez de corregirlo: si SAP
    // manda "aa 123 bb" preferimos avisarle con un 400 antes que guardar algo que no mandó.
    @Schema(
            description = "Patente en mayúsculas y sin espacios: formato viejo AAA123 o Mercosur AA123BB",
            example = "AA123BB")
    @NotBlank(message = "es obligatoria")
    @Pattern(
            regexp = "[A-Z]{3}[0-9]{3}|[A-Z]{2}[0-9]{3}[A-Z]{2}",
            message = "formato inválido, se espera AAA123 o AA123BB")
    private String plate;

    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String description;

    // Litros de cada cisterna, en orden: el primero es la posición 0, el segundo la 1, etc.
    // Se valida > 0 acá porque la base tiene CHECK(liters > 0) y sino terminaría en un 500.
    @NotEmpty(message = "debe tener al menos una cisterna")
    private List<
                    @NotNull(message = "no puede ser null")
                    @Positive(message = "debe ser mayor a 0") Double>
            tanks;
}
