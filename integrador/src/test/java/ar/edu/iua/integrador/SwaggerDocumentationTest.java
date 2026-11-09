package ar.edu.iua.integrador;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba el encabezado de Swagger y que los endpoints se agrupen por paso del circuito. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:swagger;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
@AutoConfigureMockMvc
class SwaggerDocumentationTest {
    private static final String ORDER_PATH = "$.paths['/api/v1/orders/{orderNumber}";

    @Autowired private MockMvc mvc;

    @Test
    void showsTheApiTitleVersionAndDescription() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Integrador IW3 — API de carga de GLP"))
                .andExpect(jsonPath("$.info.version").value("1.0"))
                .andExpect(jsonPath("$.info.description").value(containsString("Postman")));
    }

    // Exige cada tag una sola vez: si dos @Tag del mismo paso tuvieran distinta descripción,
    // Swagger los mostraría como dos grupos y este test fallaría.
    @Test
    void definesOneDescribedTagPerStep() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.tags[?(@.name =~ /[0-9]\\..*/)].name")
                                .value(
                                        containsInAnyOrder(
                                                "1. Recepción",
                                                "2. Pesaje inicial",
                                                "3. Activación",
                                                "4. Lecturas",
                                                "5. Cierre",
                                                "6. Pesaje final y conciliación",
                                                "7. Consultas")))
                .andExpect(
                        jsonPath("$.tags[?(@.name =~ /[0-9]\\..*/)].description")
                                .value(everyItem(not(blankOrNullString()))));
    }

    @Test
    void groupsEachEndpointInItsStep() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.tags[0]").value("1. Recepción"))
                .andExpect(jsonPath(ORDER_PATH + "/tare'].post.tags[0]").value("2. Pesaje inicial"))
                .andExpect(jsonPath(ORDER_PATH + "/activation'].post.tags[0]").value("3. Activación"))
                .andExpect(jsonPath(ORDER_PATH + "/readings'].post.tags[0]").value("4. Lecturas"))
                .andExpect(jsonPath(ORDER_PATH + "/readings'].get.tags[0]").value("4. Lecturas"))
                .andExpect(jsonPath(ORDER_PATH + "/close'].post.tags[0]").value("5. Cierre"))
                .andExpect(
                        jsonPath(ORDER_PATH + "/final-weight'].post.tags[0]")
                                .value("6. Pesaje final y conciliación"))
                .andExpect(
                        jsonPath(ORDER_PATH + "/reconciliation'].get.tags[0]")
                                .value("6. Pesaje final y conciliación"))
                .andExpect(jsonPath(ORDER_PATH + "'].get.tags[0]").value("7. Consultas"))
                .andExpect(
                        jsonPath("$.paths['/api/v1/health'].get.tags[0]")
                                .value("Salud del servicio"));
    }

    @Test
    void swaggerUiSortsTheTagsAlphabetically() throws Exception {
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagsSorter").value("alpha"));
    }
}
