package ar.edu.iua.integrador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Datos del chofer que vienen dentro de la orden de SAP. */
@Getter
@Setter
public class DriverRequest {

    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String externalCode;

    @NotBlank(message = "es obligatorio")
    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String name;

    @NotBlank(message = "es obligatorio")
    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String lastName;

    // Opcional: si no viene ni código ni documento, se crea un chofer nuevo (ver sincronización).
    // Si viene, tiene que ser solo dígitos, así "30.111.222" y "30111222" no quedan como dos personas.
    @Schema(
            description = "Documento opcional: solo dígitos, 7 u 8, sin puntos ni espacios",
            example = "30111222")
    @Pattern(regexp = "[0-9]{7,8}", message = "formato inválido, se esperan 7 u 8 dígitos sin puntos")
    private String document;
}
