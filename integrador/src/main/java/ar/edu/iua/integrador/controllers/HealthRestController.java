package ar.edu.iua.integrador.controllers;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(Constants.URL_BASE)
public class HealthRestController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "integrador");
    }
}
