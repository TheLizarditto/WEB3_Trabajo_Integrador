package ar.edu.iua.integrador.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configura el encabezado que muestra Swagger. */
@Configuration
public class OpenApiConfig {

    /**
     * Título, versión y descripción de la API. No se definen servidores a propósito: así springdoc
     * usa la URL de cada instalación en vez de un host fijo.
     */
    @Bean
    public OpenAPI integradorOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Integrador IW3 — API de carga de GLP")
                                .version("1.0")
                                .description(
                                        "Backend sin interfaz gráfica para las órdenes de carga.\n\n"
                                                + "SAP, TMS y surtidor se simulan con la colección"
                                                + " de Postman."));
    }
}
