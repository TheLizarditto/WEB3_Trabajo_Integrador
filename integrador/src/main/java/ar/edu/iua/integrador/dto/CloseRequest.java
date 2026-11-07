package ar.edu.iua.integrador.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Observación opcional del cierre de carga. */
@Getter
@Setter
public class CloseRequest {
    @Size(max = 255)
    private String observation;
}
