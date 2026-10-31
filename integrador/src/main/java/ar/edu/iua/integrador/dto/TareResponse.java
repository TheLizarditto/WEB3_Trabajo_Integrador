package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta al registrar la tara. Es la única vez que la contraseña sale del sistema: en la base
 * se guarda solo su hash, así que después no se puede volver a consultar.
 */
@Getter
@AllArgsConstructor
public class TareResponse {

    @Schema(example = "501")
    private long orderNumber;

    @Schema(description = "Peso del camión vacío registrado, en kg", example = "8500")
    private double tare;

    @Schema(
            description = "Contraseña de activación de 5 dígitos para el surtidor. Solo se devuelve acá",
            example = "04217")
    private String password;
}
