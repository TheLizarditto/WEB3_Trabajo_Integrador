package ar.edu.iua.integrador.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** JSON que manda SAP para dar de alta una orden de carga (estado 1). */
@Getter
@Setter
public class SapOrderRequest {

    @NotNull(message = "es obligatorio")
    @Positive(message = "debe ser mayor a 0")
    private Long orderNumber;

    // Opcional: el profe confirmó que SAP puede no mandar código externo.
    @Size(max = 100, message = "no puede superar los 100 caracteres")
    private String externalCode;

    // Se espera en formato ISO con zona, por ejemplo "2026-11-05T10:00:00Z".
    @NotNull(message = "es obligatoria")
    private Instant scheduledAt;

    // Kilos a cargar.
    @NotNull(message = "es obligatorio")
    @Positive(message = "debe ser mayor a 0")
    private Double preset;

    // @Valid hace que también se validen los campos de adentro de cada objeto.
    @NotNull(message = "es obligatorio")
    @Valid
    private TruckRequest truck;

    @NotNull(message = "es obligatorio")
    @Valid
    private DriverRequest driver;

    @NotNull(message = "es obligatorio")
    @Valid
    private CustomerRequest customer;

    @NotNull(message = "es obligatorio")
    @Valid
    private ProductRequest product;
}
