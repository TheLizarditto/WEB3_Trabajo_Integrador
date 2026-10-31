package ar.edu.iua.integrador;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Prueba los endpoints de órdenes de punta a punta: JSON → validación → negocio → base → JSON.
 * Cada test usa su propio número de orden y patente para no chocar con los demás.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:rest;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
@AutoConfigureMockMvc
class OrderRestControllerTest {
    @Autowired private MockMvc mvc;

    private String json(long number, String plate) {
        return """
                {
                  "orderNumber": %d,
                  "scheduledAt": "2026-11-05T10:00:00Z",
                  "preset": 15000,
                  "truck": { "plate": "%s", "description": "Scania", "tanks": [10000, 5000] },
                  "driver": { "name": "Ana", "lastName": "Pérez", "document": "30111222" },
                  "customer": { "name": "Gas del Sur SA" },
                  "product": { "name": "GLP" }
                }
                """
                .formatted(number, plate);
    }

    private ResultActions postOrder(String body) throws Exception {
        return mvc.perform(
                post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validOrderIsCreatedAndCanBeLoaded() throws Exception {
        // El Location lleva el número de orden, no el id interno.
        postOrder(json(2001, "AC100DE"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/2001"))
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.truck.plate").value("AC100DE"));

        mvc.perform(get("/api/v1/orders/2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(2001));
    }

    @Test
    void repeatedOrderNumberIs409() throws Exception {
        postOrder(json(2002, "AC200DE")).andExpect(status().isCreated());

        postOrder(json(2002, "AC200DE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(containsString("2002")));
    }

    @Test
    void plateWithSpacesAndLowercaseIs400() throws Exception {
        postOrder(json(2003, "aa 123 bb"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("truck.plate: formato inválido, se espera AAA123 o AA123BB"));
    }

    @Test
    void plateWithWrongFormatIs400() throws Exception {
        postOrder(json(2004, "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("truck.plate")));
    }

    @Test
    void missingPresetIs400() throws Exception {
        postOrder(json(2005, "AC500DE").replace("\"preset\": 15000,", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("preset: es obligatorio"));
    }

    @Test
    void missingOrderIs404() throws Exception {
        mvc.perform(get("/api/v1/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe la orden número 999999"));
    }

    @Test
    void swaggerShowsTheExpectedPlateAndDocumentFormats() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.TruckRequest.properties.plate.example")
                        .value("AA123BB"))
                .andExpect(jsonPath("$.components.schemas.TruckRequest.properties.plate.description")
                        .value(containsString("AAA123")))
                .andExpect(jsonPath("$.components.schemas.DriverRequest.properties.document.example")
                        .value("30111222"));
    }
}
