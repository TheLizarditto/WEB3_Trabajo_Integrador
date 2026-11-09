package ar.edu.iua.integrador.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(Constants.URL_BASE)
// Sin número a propósito: no es un paso del circuito y así queda después de los que sí lo son.
@Tag(name = "Salud del servicio", description = "Indica si el proceso web responde")
public class HealthRestController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "integrador");
    }
}
