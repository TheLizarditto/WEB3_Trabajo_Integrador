package ar.edu.iua.integrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Datos del producto que vienen dentro de la orden de SAP. */
@Getter
@Setter
public class ProductRequest {

    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String externalCode;

    @NotBlank(message = "es obligatorio")
    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String name;

    @Size(max = 255, message = "no puede superar los 255 caracteres")
    private String description;
}
