package ar.edu.iua.integrador;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Endpoints de tara y activación de punta a punta: JSON → validación → negocio → base → JSON.
 * Cada test usa su propio número de orden y patente para no chocar con los demás.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tareRest;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
@AutoConfigureMockMvc
class TareActivationRestControllerTest {
    @Autowired private MockMvc mvc;

    // Crea por la API una orden en estado 1.
    private void createOrder(long number, String plate) throws Exception {
        String body =
                """
                {
                  "orderNumber": %d,
                  "scheduledAt": "2026-11-05T10:00:00Z",
                  "preset": 15000,
                  "truck": { "plate": "%s", "tanks": [10000, 5000] },
                  "driver": { "name": "Ana", "lastName": "Pérez" },
                  "customer": { "name": "Gas del Sur SA" },
                  "product": { "name": "GLP" }
                }
                """
                        .formatted(number, plate);
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private ResultActions postTare(long number, String body) throws Exception {
        return mvc.perform(
                post("/api/v1/orders/" + number + "/tare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions postActivation(long number, String password) throws Exception {
        return mvc.perform(
                post("/api/v1/orders/" + number + "/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + password + "\"}"));
    }

    // Registra la tara y devuelve la contraseña que vino en la respuesta.
    private String tare(long number) throws Exception {
        String json =
                postTare(number, "{\"tare\": 8500}")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(json, "$.password");
    }

    @Test
    void fullTareAndActivationFlow() throws Exception {
        createOrder(3201, "AD201EF");
        // Antes de pesar, la tara es null y no 0.
        mvc.perform(get("/api/v1/orders/3201"))
                .andExpect(jsonPath("$.tare").value(nullValue()))
                .andExpect(jsonPath("$.finalWeight").value(nullValue()));

        postTare(3201, "{\"tare\": 8500}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(3201))
                .andExpect(jsonPath("$.tare").value(8500.0))
                .andExpect(jsonPath("$.password").value(matchesPattern("[0-9]{5}")));

        // Después, el GET no muestra ni la contraseña ni su hash.
        mvc.perform(get("/api/v1/orders/3201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(2))
                .andExpect(jsonPath("$.tare").value(8500.0))
                .andExpect(jsonPath("$.initialWeighedAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.activationHash").doesNotExist());
    }

    @Test
    void correctPasswordReturnsPreset() throws Exception {
        createOrder(3202, "AD202EF");
        String password = tare(3202);

        postActivation(3202, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(3202))
                .andExpect(jsonPath("$.preset").value(15000.0));
        mvc.perform(get("/api/v1/orders/3202")).andExpect(jsonPath("$.activatedAt").exists());
    }

    @Test
    void wrongPasswordIs403() throws Exception {
        createOrder(3203, "AD203EF");
        String password = tare(3203);
        String wrong = String.format("%05d", (Integer.parseInt(password) + 1) % 100_000);

        postActivation(3203, wrong)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.preset").doesNotExist());
    }

    @Test
    void passwordWithoutFiveDigitsIs400() throws Exception {
        createOrder(3204, "AD204EF");
        tare(3204);

        postActivation(3204, "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: debe tener exactamente 5 dígitos"));
    }

    @Test
    void activationBeforeTareIs409() throws Exception {
        createOrder(3205, "AD205EF");

        postActivation(3205, "12345")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void secondTareIs409() throws Exception {
        createOrder(3206, "AD206EF");
        tare(3206);

        postTare(3206, "{\"tare\": 9000}").andExpect(status().isConflict());
    }

    @Test
    void invalidTareIs400() throws Exception {
        createOrder(3207, "AD207EF");

        postTare(3207, "{\"tare\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tare: debe ser mayor a 0"));
        postTare(3207, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tare: es obligatoria"));
        // Los rechazos no tocaron la orden.
        mvc.perform(get("/api/v1/orders/3207")).andExpect(jsonPath("$.state").value(1));
    }

    @Test
    void tareOfMissingOrderIs404() throws Exception {
        postTare(399999, "{\"tare\": 8500}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe la orden número 399999"));
    }
}
