package ar.edu.iua.integrador;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reading-rest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP",
    "integrador.readings.storage-interval-seconds=3600"
})
@AutoConfigureMockMvc
class ReadingRestControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    private String json(long number, String plate, String document) {
        return """
            {"orderNumber":%d,"scheduledAt":"2026-11-05T10:00:00Z","preset":15000,
            "truck":{"plate":"%s","description":"Scania","tanks":[10000]},
            "driver":{"name":"Ana","lastName":"Perez","document":"%s"},
            "customer":{"name":"Gas del Sur"},"product":{"name":"GLP"}}
            """.formatted(number, plate, document);
    }

    private void createAndActivate(long number, String plate, String document) throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(json(number, plate, document)))
                .andExpect(status().isCreated());
        MvcResult tare = mvc.perform(post("/api/v1/orders/{n}/tare", number)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tare", 8000))))
                .andExpect(status().isOk()).andReturn();
        String password = mapper.readTree(tare.getResponse().getContentAsString()).get("password").asText();
        mvc.perform(post("/api/v1/orders/{n}/activation", number).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("password", password))))
                .andExpect(status().isOk());
    }

    @Test
    void recordsReadingByInternalOrderNumberAndReturnsHistory() throws Exception {
        createAndActivate(4201, "AG101HJ", "42011001");
        mvc.perform(post("/api/v1/orders/4201/readings").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mass", 100, "density", .55, "temperature", 24, "flow", 1200))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.stored").value(true)).andExpect(jsonPath("$.order.startedAt").exists());
        mvc.perform(get("/api/v1/orders/4201/readings")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].density").value(.55));
    }

    @Test
    void invalidDensityAndMissingFieldsAreBadRequestAndBusinessDiscardsAreExplicit() throws Exception {
        createAndActivate(4202, "AG102HJ", "42011002");
        mvc.perform(post("/api/v1/orders/4202/readings").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mass", 100, "density", 1.1, "temperature", 24, "flow", 1200))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("density")));
        mvc.perform(post("/api/v1/orders/4202/readings").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mass", 100))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders/4202/readings").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mass", 0, "density", .5, "temperature", 24, "flow", 1200))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
    }

    @Test
    void readingRequiresActivatedStateTwoAndSwaggerDeclaresDensityUnit() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(4203, "AG103HJ", "42011003")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders/4203/readings").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("mass", 1, "density", .5, "temperature", 24, "flow", 1200))))
                .andExpect(status().isConflict());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNumber}/readings'].post").exists())
                .andExpect(jsonPath("$.components.schemas.ReadingRequest.properties.density.description")
                        .value(containsString("g/cm3")));
    }
}
