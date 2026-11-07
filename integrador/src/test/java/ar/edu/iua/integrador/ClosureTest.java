package ar.edu.iua.integrador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.InvalidDataException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.dto.ReadingRequest;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:closure;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP",
    "integrador.readings.storage-interval-seconds=3600"
})
@AutoConfigureMockMvc
class ClosureTest {
    private static final AtomicLong NUMBERS = new AtomicLong(6000);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private IOrderBusiness orders;
    @Autowired private OrderRepository repository;
    @MockitoSpyBean private StateTransitionRepository transitions;

    private long createOrder(boolean active) throws Exception {
        long number = NUMBERS.incrementAndGet();
        String body = """
                {"orderNumber":%d,"scheduledAt":"2026-11-05T10:00:00Z","preset":15000,
                 "truck":{"plate":"AC%03dGH","tanks":[10000]},
                 "driver":{"name":"Ana","lastName":"Perez"},
                 "customer":{"name":"Gas del Sur"},"product":{"name":"GLP"}}
                """.formatted(number, number - 6000);
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        if (active) {
            var tare = orders.registerTare(number, 8000);
            orders.activate(number, tare.getPassword());
        }
        return number;
    }

    private void reading(long number, double mass, double temperature) throws Exception {
        var request = new ReadingRequest();
        request.setMass(mass);
        request.setDensity(.5);
        request.setTemperature(temperature);
        request.setFlow(1000.0);
        orders.registerReading(number, request);
    }

    private JsonNode postJson(long number, String action, String body, int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/orders/{n}/{action}", number, action)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }

    @Test
    void closureBlocksReadingsAndAuditsBothTransitions() throws Exception {
        long number = createOrder(true);
        reading(number, 100, 20);
        var before = orders.loadByOrderNumber(number);
        postJson(number, "close", "{\"observation\":\"Carga completa\"}", 200);
        postJson(number, "readings", "{\"mass\":200,\"density\":0.5,\"temperature\":24,\"flow\":1000}", 409);
        postJson(number, "activation", "{\"password\":\"12345\"}", 409);
        var closed = orders.loadByOrderNumber(number);
        assertThat(closed.getState()).isEqualTo(3);
        assertThat(closed.getActivationHash()).isNull();
        assertThat(closed.getLastMass()).isEqualTo(before.getLastMass());
        assertThat(closed.getFinishedAt()).isEqualTo(before.getFinishedAt());
        assertThat(orders.readings(number)).hasSize(1);
        postJson(number, "close", "{}", 409);
        postJson(number, "final-weight", "{\"weight\":8200,\"observation\":\"Balanza final\"}", 200);
        postJson(number, "readings", "{\"mass\":300,\"density\":0.5,\"temperature\":24,\"flow\":1000}", 409);
        postJson(number, "final-weight", "{\"weight\":8200}", 409);
        var finished = orders.loadByOrderNumber(number);
        assertThat(finished.getState()).isEqualTo(4);
        assertThat(finished.getFinalWeighedAt()).isNotNull();
        var history = transitions.findByOrderIdOrderByOccurredAtAsc(finished.getId());
        assertThat(history).extracting(t -> t.getToState()).containsExactly(1, 2, 3, 4);
        assertThat(history.get(2).getFromState()).isEqualTo(2);
        assertThat(history.get(2).getObservation()).isEqualTo("Carga completa");
        assertThat(history.get(3).getFromState()).isEqualTo(3);
        assertThat(history.get(3).getObservation()).isEqualTo("Balanza final");
    }

    @Test
    void reconciliationUsesHeaderMassAndSampledAveragesAndRemainsQueryable() throws Exception {
        long number = createOrder(true);
        reading(number, 100, 20);
        reading(number, 200, 90); // No se almacena: el intervalo es una hora.
        orders.close(number, null);
        mvc.perform(get("/api/v1/orders/{n}/reconciliation", number)).andExpect(status().isConflict());
        JsonNode result = postJson(number, "final-weight", "{\"weight\":8250}", 200);
        assertThat(result.get("loadedProduct").asDouble()).isEqualTo(200);
        assertThat(result.get("netWeight").asDouble()).isEqualTo(250);
        assertThat(result.get("difference").asDouble()).isEqualTo(50);
        assertThat(result.get("averageTemperature").asDouble()).isEqualTo(20);
        assertThat(result.get("averageDensity").asDouble()).isEqualTo(.5);
        assertThat(result.get("averageFlow").asDouble()).isEqualTo(1000);
        assertThat(result.get("unit").asText()).isEqualTo("kg");
        var query = mvc.perform(get("/api/v1/orders/{n}/reconciliation", number))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(query.getResponse().getContentAsString())).isEqualTo(result);
        assertThat(orders.reconciliation(number)).isEqualTo(orders.reconciliation(number));
    }

    @Test
    void averagesIncludeAllStoredDetails() throws Exception {
        long number = createOrder(true);
        reading(number, 100, 20);
        var order = orders.loadByOrderNumber(number);
        order.setLastStoredAt(order.getLastStoredAt().minusSeconds(3601));
        repository.saveAndFlush(order);
        reading(number, 200, 30);
        orders.close(number, null);
        assertThat(orders.finalWeight(number, 8200, null).averageTemperature()).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"weight\":null}", "{\"weight\":-1}", "{\"weight\":0}",
            "{\"weight\":7999}", "{\"weight\":1000001}", "{\"weight\":\"abc\"}"})
    void invalidFinalWeightLeavesStateAndAuditUnchanged(String body) throws Exception {
        long number = createOrder(true);
        orders.close(number, null);
        postJson(number, "final-weight", body, 400);
        var order = orders.loadByOrderNumber(number);
        assertThat(order.getState()).isEqualTo(3);
        assertThat(order.getFinalWeight()).isNull();
        assertThat(order.getFinalWeighedAt()).isNull();
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(order.getId())).hasSize(3);
    }

    @Test
    void finiteValidationAlsoAppliesToBusinessCalls() throws Exception {
        long number = createOrder(true);
        orders.close(number, null);
        for (double weight : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThatThrownBy(() -> orders.finalWeight(number, weight, null))
                    .isInstanceOf(InvalidDataException.class);
        }
        assertThat(orders.loadByOrderNumber(number).getState()).isEqualTo(3);
    }

    @Test
    void closingWithoutReadingsAllowsEqualTareAndNullAverages() throws Exception {
        long number = createOrder(true);
        orders.close(number, null);
        var result = orders.finalWeight(number, 8000, null);
        assertThat(result.loadedProduct()).isZero();
        assertThat(result.netWeight()).isZero();
        assertThat(result.difference()).isZero();
        assertThat(result.averageTemperature()).isNull();
        assertThat(result.averageDensity()).isNull();
        assertThat(result.averageFlow()).isNull();
    }

    @Test
    void wrongStatesAndUnknownOrdersHaveExpectedHttpResponses() throws Exception {
        long number = createOrder(false);
        postJson(number, "close", "{}", 409);
        postJson(number, "final-weight", "{\"weight\":8200}", 409);
        mvc.perform(get("/api/v1/orders/{n}/reconciliation", number)).andExpect(status().isConflict());
        orders.registerTare(number, 8000);
        postJson(number, "final-weight", "{\"weight\":8200}", 409);
        mvc.perform(get("/api/v1/orders/{n}/reconciliation", number)).andExpect(status().isConflict());
        postJson(999999, "close", "{}", 404);
        postJson(999999, "final-weight", "{\"weight\":8200}", 404);
        mvc.perform(get("/api/v1/orders/999999/reconciliation")).andExpect(status().isNotFound());
    }

    @Test
    void oversizedObservationsAreRejectedAndSwaggerExposesEndpoints() throws Exception {
        long number = createOrder(true);
        String observation = "x".repeat(256);
        postJson(number, "close", mapper.writeValueAsString(Map.of("observation", observation)), 400);
        assertThat(orders.loadByOrderNumber(number).getState()).isEqualTo(2);
        orders.close(number, null);
        postJson(number, "final-weight", mapper.writeValueAsString(Map.of("weight", 8200,
                "observation", observation)), 400);
        assertThat(orders.loadByOrderNumber(number).getState()).isEqualTo(3);
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNumber}/close'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNumber}/final-weight'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderNumber}/reconciliation'].get").exists());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedAuditRollsBackClosureOrFinalWeight(boolean finalWeighing) throws Exception {
        long number = createOrder(true);
        reading(number, 100, 20);
        if (finalWeighing) orders.close(number, null);
        var before = orders.loadByOrderNumber(number);
        int transitionCount = transitions.findByOrderIdOrderByOccurredAtAsc(before.getId()).size();
        doThrow(new IllegalStateException("Falla de auditoría simulada")).when(transitions).save(any());
        assertThatThrownBy(() -> {
            if (finalWeighing) orders.finalWeight(number, 8200, null);
            else orders.close(number, null);
        }).isInstanceOf(IllegalStateException.class);
        var after = orders.loadByOrderNumber(number);
        assertThat(after.getState()).isEqualTo(before.getState());
        assertThat(after.getActivationHash()).isEqualTo(before.getActivationHash());
        assertThat(after.getFinalWeight()).isNull();
        assertThat(after.getFinalWeighedAt()).isNull();
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(before.getId())).hasSize(transitionCount);
    }

    @Test
    void concurrentClosuresProduceOneTransitionAndOneConflict() throws Exception {
        long number = createOrder(true);
        reading(number, 100, 20);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> close = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Inicio concurrente agotado");
                try {
                    orders.close(number, "Cierre concurrente");
                    return true;
                } catch (InvalidStateException ex) {
                    return false;
                }
            };
            var first = executor.submit(close);
            var second = executor.submit(close);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
        var order = orders.loadByOrderNumber(number);
        assertThat(order.getState()).isEqualTo(3);
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(order.getId()))
                .filteredOn(t -> t.getToState() == 3).hasSize(1);
    }
}
