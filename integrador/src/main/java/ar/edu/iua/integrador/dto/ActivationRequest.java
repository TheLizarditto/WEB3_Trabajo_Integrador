package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** JSON que manda el surtidor para activarse con la contraseña que se generó en la tara. */
@Getter
@Setter
public class ActivationRequest {

    // String y no número: "04217" tiene que conservar el cero de adelante.
    // @NotBlank porque @Pattern deja pasar el null.
    @Schema(description = "Contraseña de 5 dígitos", example = "04217")
    @NotBlank(message = "es obligatoria")
    @Pattern(regexp = "[0-9]{5}", message = "debe tener exactamente 5 dígitos")
    private String password;
}
