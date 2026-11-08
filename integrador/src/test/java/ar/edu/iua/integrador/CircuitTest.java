package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.dto.ReadingRequest;
import ar.edu.iua.integrador.dto.ReconciliationResponse;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.StateTransition;
import ar.edu.iua.integrador.model.persistence.LoadDetailRepository;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;
import ar.edu.iua.integrador.model.persistence.TruckRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Recorre el circuito completo de una orden (puntos 1 a 5 de la consigna) a través de la API, como
 * lo harían SAP, el TMS y el surtidor. Las pruebas de cada entrega validan su paso por separado;
 * acá se valida que los pasos funcionen juntos, que ninguna operación fuera de secuencia altere la
 * orden y que el resultado quede persistido.
 *
 * <p>Usa H2 en modo MySQL: no reemplaza la verificación contra un servidor MySQL real.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=" + CircuitTest.DB_URL,
            "spring.datasource.username=" + CircuitTest.DB_USER,
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP",
            "integrador.readings.storage-interval-seconds=3600"
        })
@AutoConfigureMockMvc
class CircuitTest {

    static final String DB_URL = "jdbc:h2:mem:circuit;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    static final String DB_USER = "sa";
    private static final long UNKNOWN_ORDER = 999_999_999L;
    private static final double TARE = 8000;
    private static final String READING_BODY =
            "{\"mass\":100,\"density\":0.55,\"temperature\":20,\"flow\":1000}";

    // Cada orden usa un número y una patente propios porque la base se comparte dentro de la clase.
    private static final AtomicLong NUMBERS = new AtomicLong(7000);

    /** Momentos del circuito desde los que se prueban las operaciones. */
    private enum Stage {
        PENDING,
        WEIGHED,
        ACTIVATED,
        LOADING,
        CLOSED,
        FINISHED
    }

    private record CircuitOrder(long number, String password) {}

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private IOrderBusiness orders;
    @Autowired private OrderRepository orderRepository;
    @Autowired private StateTransitionRepository transitions;
    @Autowired private LoadDetailRepository details;
    @Autowired private TruckRepository truckRepository;

    // ---------------------------------------------------------------------------------------------
    // Circuito completo
    // ---------------------------------------------------------------------------------------------

    @Test
    void fullCircuitFromSapToReconciliation() throws Exception {
        long number = NUMBERS.incrementAndGet();

        // Punto 1: SAP envía la orden, que queda en estado 1 y sin datos de pesaje ni de carga.
        mvc.perform(json(post("/api/v1/orders"), sapOrder(number, "SAP-" + number)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + number))
                .andExpect(jsonPath("$.state").value(1))
                .andExpect(jsonPath("$.tare").value(nullValue()))
                .andExpect(jsonPath("$.lastMass").value(nullValue()));
        mvc.perform(get("/api/v1/orders/{n}", number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalCode").value("SAP-" + number))
                .andExpect(jsonPath("$.truck.plate").value(plate(number)))
                .andExpect(jsonPath("$.driver.lastName").value("Pérez"))
                .andExpect(jsonPath("$.customer.name").value("Gas del Sur " + number))
                .andExpect(jsonPath("$.product.name").value("GLP " + number))
                .andExpect(jsonPath("$.preset").value(15000.0));

        // Punto 2: el TMS manda la tara y recibe la contraseña, por única vez.
        JsonNode tare = send(number, "tare", "{\"tare\":8000}", 200);
        String password = tare.get("password").asText();
        assertThat(password).matches("[0-9]{5}");
        JsonNode weighed = read("/api/v1/orders/" + number, 200);
        assertThat(weighed.get("state").asInt()).isEqualTo(2);
        assertThat(weighed.get("tare").asDouble()).isEqualTo(TARE);
        assertThat(weighed.has("password")).isFalse();
        assertThat(weighed.has("activationHash")).isFalse();

        // Punto 3: la contraseña correcta devuelve el preset; repetirla antes de cargar es válido.
        assertThat(send(number, "activation", passwordBody(password), 200).get("preset").asDouble())
                .isEqualTo(15000);
        Instant activatedAt = order(number).getActivatedAt();
        send(number, "activation", passwordBody(password), 200);
        assertThat(order(number).getActivatedAt()).isEqualTo(activatedAt);

        // Punto 3: la primera lectura se almacena; la segunda solo actualiza la cabecera (muestreo).
        JsonNode first = send(number, "readings", reading(1000, 0.50, 20, 3600), 200);
        assertThat(first.get("accepted").asBoolean()).isTrue();
        assertThat(first.get("stored").asBoolean()).isTrue();
        assertThat(first.at("/order/startedAt").isNull()).isFalse();
        // Una vez que empezó la carga el surtidor ya no se puede volver a activar.
        send(number, "activation", passwordBody(password), 409);

        JsonNode second = send(number, "readings", reading(2000, 0.60, 90, 1800), 200);
        assertThat(second.get("accepted").asBoolean()).isTrue();
        assertThat(second.get("stored").asBoolean()).isFalse();
        assertThat(second.at("/order/lastMass").asDouble()).isEqualTo(2000);
        assertThat(second.at("/order/lastTemperature").asDouble()).isEqualTo(90);
        assertThat(second.at("/order/etaSeconds").asDouble()).isEqualTo((15000 - 2000) / 1800.0 * 3600);
        assertThat(second.get("order").has("activationHash")).isFalse();

        JsonNode history = read("/api/v1/orders/" + number + "/readings", 200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("mass").asDouble()).isEqualTo(1000);

        // Punto 4: el cierre pasa a estado 3 y bloquea nuevas lecturas.
        JsonNode closed = send(number, "close", "{\"observation\":\"Carga completa\"}", 200);
        assertThat(closed.get("state").asInt()).isEqualTo(3);
        send(number, "readings", reading(2500, 0.60, 20, 1800), 409);
        assertThat(order(number).getActivationHash()).isNull();

        // Punto 5: el pesaje final pasa a estado 4 y devuelve la conciliación.
        JsonNode reconciliation =
                send(number, "final-weight", "{\"weight\":10010,\"observation\":\"Balanza final\"}", 200);
        assertThat(reconciliation.get("orderNumber").asLong()).isEqualTo(number);
        assertThat(reconciliation.get("tare").asDouble()).isEqualTo(TARE);
        assertThat(reconciliation.get("finalWeight").asDouble()).isEqualTo(10010);
        // Producto cargado: última masa aceptada, aunque esa lectura no se haya almacenado.
        assertThat(reconciliation.get("loadedProduct").asDouble()).isEqualTo(2000);
        assertThat(reconciliation.get("netWeight").asDouble()).isEqualTo(2010);
        assertThat(reconciliation.get("difference").asDouble()).isEqualTo(10);
        // Promedios: solo sobre los datos almacenados, por eso la segunda lectura no los mueve.
        assertThat(reconciliation.get("averageTemperature").asDouble()).isEqualTo(20);
        assertThat(reconciliation.get("averageDensity").asDouble()).isEqualTo(0.50);
        assertThat(reconciliation.get("averageFlow").asDouble()).isEqualTo(3600);

        // La conciliación se puede volver a pedir sin repetir el pesaje y da lo mismo.
        assertThat(read("/api/v1/orders/" + number + "/reconciliation", 200)).isEqualTo(reconciliation);

        Order finished = order(number);
        assertThat(finished.getState()).isEqualTo(4);
        assertChronological(finished);
        assertAudited(finished, Map.of(3, "Carga completa", 4, "Balanza final"));
    }

    @Test
    void circuitAlsoWorksWithoutExternalCodes() throws Exception {
        // El código externo es opcional: la orden y sus maestros se identifican por sus datos.
        long number = NUMBERS.incrementAndGet();
        mvc.perform(json(post("/api/v1/orders"), sapOrder(number, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalCode").value(nullValue()));
        String password = send(number, "tare", "{\"tare\":8000}", 200).get("password").asText();
        send(number, "activation", passwordBody(password), 200);
        send(number, "readings", READING_BODY, 200);
        send(number, "close", "{}", 200);
        JsonNode reconciliation = send(number, "final-weight", "{\"weight\":8100}", 200);

        assertThat(reconciliation.get("externalCode").isNull()).isTrue();
        assertThat(reconciliation.get("difference").asDouble()).isZero();
        assertAudited(order(number), Map.of());
    }

    /** Repetir el circuito con los mismos maestros (como la colección Postman) los actualiza. */
    @Test
    void repeatingTheCircuitWithTheSameMastersUpdatesThem() throws Exception {
        for (String tanks : List.of("[10000,5000]", "[10000,5000]", "[20000]")) {
            long number = NUMBERS.incrementAndGet();
            String body =
                    """
                    {"orderNumber":%d,"scheduledAt":"2026-11-10T10:00:00Z","preset":15000,
                     "truck":{"externalCode":"CIRCUIT-T","plate":"AK100LM","tanks":%s},
                     "driver":{"externalCode":"CIRCUIT-D","name":"Ana","lastName":"Pérez","document":"31000000"},
                     "customer":{"externalCode":"CIRCUIT-C","name":"Cliente"},
                     "product":{"externalCode":"CIRCUIT-P","name":"GLP"}}
                    """
                            .formatted(number, tanks);
            mvc.perform(json(post("/api/v1/orders"), body)).andExpect(status().isCreated());
            String password = send(number, "tare", "{\"tare\":8000}", 200).get("password").asText();
            send(number, "activation", passwordBody(password), 200);
            send(number, "readings", READING_BODY, 200);
            send(number, "close", "{}", 200);
            send(number, "final-weight", "{\"weight\":8100}", 200);
        }

        assertThat(truckRepository.findOneByExternalCode("CIRCUIT-T").orElseThrow().getTanks())
                .singleElement()
                .satisfies(tank -> assertThat(tank.getLiters()).isEqualTo(20000));
    }

    @Test
    void twoOrdersKeepTheirDataSeparate() throws Exception {
        CircuitOrder first = orderAt(Stage.LOADING);
        CircuitOrder second = orderAt(Stage.ACTIVATED);
        send(second.number(), "readings", reading(777, 0.40, 30, 500), 200);
        send(first.number(), "close", "{}", 200);

        assertThat(order(first.number()).getLastMass()).isEqualTo(100);
        assertThat(order(first.number()).getState()).isEqualTo(3);
        assertThat(order(second.number()).getLastMass()).isEqualTo(777);
        assertThat(order(second.number()).getState()).isEqualTo(2);
        assertThat(details.findByOrderIdOrderByReceivedAtAsc(order(second.number()).getId()))
                .singleElement()
                .satisfies(detail -> assertThat(detail.getMass()).isEqualTo(777));
    }

    // ---------------------------------------------------------------------------------------------
    // Matriz de errores y transiciones
    // ---------------------------------------------------------------------------------------------

    /** Los estados son secuenciales: lo que no corresponde al momento da 409 y no cambia nada. */
    @ParameterizedTest(name = "{0} rechaza {1}")
    @CsvSource({
        "PENDING, activation", "PENDING, readings", "PENDING, close", "PENDING, final-weight",
        "PENDING, reconciliation",
        "WEIGHED, tare", "WEIGHED, readings", "WEIGHED, final-weight", "WEIGHED, reconciliation",
        "ACTIVATED, tare", "ACTIVATED, final-weight", "ACTIVATED, reconciliation",
        "LOADING, tare", "LOADING, activation", "LOADING, final-weight", "LOADING, reconciliation",
        "CLOSED, tare", "CLOSED, activation", "CLOSED, readings", "CLOSED, close",
        "CLOSED, reconciliation",
        "FINISHED, tare", "FINISHED, activation", "FINISHED, readings", "FINISHED, close",
        "FINISHED, final-weight"
    })
    void operationsOutOfSequenceAreRejectedWithoutChanges(Stage stage, String operation)
            throws Exception {
        CircuitOrder order = orderAt(stage);
        List<Object> before = snapshot(order.number());

        assertStandardError(perform(order, operation, validBody(order, operation), 409), 409);
        assertThat(snapshot(order.number())).isEqualTo(before);
    }

    /** Los datos inválidos dan 400 con el formato uniforme de error y no cambian nada. */
    @ParameterizedTest(name = "{1} en {0}: {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '`',
            textBlock =
                    """
                    PENDING   | tare         | {}
                    PENDING   | tare         | {"tare":null}
                    PENDING   | tare         | {"tare":0}
                    PENDING   | tare         | {"tare":-1}
                    PENDING   | tare         | {"tare":"abc"}
                    PENDING   | tare         | {"tare":1000001}
                    PENDING   | tare         | {"tare":1e400}
                    PENDING   | tare         | no es json
                    PENDING   | tare         | ``
                    WEIGHED   | activation   | {}
                    WEIGHED   | activation   | {"password":"123"}
                    WEIGHED   | activation   | {"password":"123456"}
                    WEIGHED   | activation   | {"password":"abcde"}
                    ACTIVATED | readings     | {}
                    ACTIVATED | readings     | {"density":0.5,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":100,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":100,"density":0.5,"flow":1000}
                    ACTIVATED | readings     | {"mass":100,"density":0.5,"temperature":20}
                    ACTIVATED | readings     | {"mass":100,"density":1.5,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":100,"density":-0.1,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":"abc","density":0.5,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":1e400,"density":0.5,"temperature":20,"flow":1000}
                    ACTIVATED | readings     | {"mass":100,"density":0.5,"temperature":1e400,"flow":1000}
                    ACTIVATED | close        | no es json
                    ACTIVATED | close        | ``
                    CLOSED    | final-weight | {}
                    CLOSED    | final-weight | {"weight":0}
                    CLOSED    | final-weight | {"weight":-1}
                    CLOSED    | final-weight | {"weight":7999}
                    CLOSED    | final-weight | {"weight":1000001}
                    CLOSED    | final-weight | {"weight":1e400}
                    CLOSED    | final-weight | no es json
                    """)
    void invalidDataIsRejectedWithoutChanges(Stage stage, String operation, String body)
            throws Exception {
        CircuitOrder order = orderAt(stage);
        List<Object> before = snapshot(order.number());

        assertStandardError(perform(order, operation, body, 400), 400);
        assertThat(snapshot(order.number())).isEqualTo(before);
    }

    /** Los mensajes de validación van en español sin depender del idioma de la máquina. */
    @ParameterizedTest(name = "{1} en {0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    CLOSED    | final-weight | {}                    | weight: es obligatorio
                    CLOSED    | final-weight | {"weight":-1}         | weight: debe ser mayor a 0
                    CLOSED    | final-weight | {"weight":1000001}    | weight: no puede superar los 1000000 kg
                    PENDING   | tare         | {"tare":1000001}      | tare: no puede superar los 1000000 kg
                    """)
    void validationMessagesAreInSpanish(Stage stage, String operation, String body, String message)
            throws Exception {
        CircuitOrder order = orderAt(stage);
        assertThat(perform(order, operation, body, 400).get("message").asText()).isEqualTo(message);
    }

    @Test
    void oversizedObservationMessagesAreInSpanish() throws Exception {
        String body = mapper.writeValueAsString(Map.of("observation", "x".repeat(256)));
        assertThat(perform(orderAt(Stage.ACTIVATED), "close", body, 400).get("message").asText())
                .isEqualTo("observation: no puede superar los 255 caracteres");
    }

    @ParameterizedTest(name = "SAP: {0}")
    @CsvSource({"preset negativo, -1", "preset cero, 0", "preset desbordado, 1e400"})
    void sapOrderWithInvalidPresetIsNotCreated(String description, String preset) throws Exception {
        long number = NUMBERS.incrementAndGet();
        String body = sapOrder(number, null).replace("\"preset\":15000", "\"preset\":" + preset);

        mvc.perform(json(post("/api/v1/orders"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("preset")));
        read("/api/v1/orders/" + number, 404);
    }

    @ParameterizedTest(name = "SAP: cisterna de {0} litros")
    @CsvSource({"-1", "0", "1e400"})
    void sapOrderWithInvalidTankIsNotCreated(String liters) throws Exception {
        long number = NUMBERS.incrementAndGet();
        String body = sapOrder(number, null).replace("[10000,5000]", "[" + liters + "]");

        mvc.perform(json(post("/api/v1/orders"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tanks")));
        read("/api/v1/orders/" + number, 404);
    }

    @ParameterizedTest(name = "{0} de una orden inexistente")
    @CsvSource({"tare", "activation", "readings", "close", "final-weight", "reconciliation", "history", "order"})
    void unknownOrdersAnswerNotFound(String operation) throws Exception {
        CircuitOrder unknown = new CircuitOrder(UNKNOWN_ORDER, "12345");
        assertStandardError(perform(unknown, operation, validBody(unknown, operation), 404), 404);
    }

    @Test
    void wrongPasswordDoesNotActivateAndCorrectOneStillWorks() throws Exception {
        CircuitOrder order = orderAt(Stage.WEIGHED);
        List<Object> before = snapshot(order.number());
        String wrong = order.password().equals("00000") ? "00001" : "00000";

        assertStandardError(send(order.number(), "activation", passwordBody(wrong), 403), 403);

        assertThat(snapshot(order.number())).isEqualTo(before);
        send(order.number(), "activation", passwordBody(order.password()), 200);
        assertThat(order(order.number()).getActivatedAt()).isNotNull();
    }

    @ParameterizedTest(name = "se descarta {0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    masa menor a la anterior | {"mass":50,"density":0.9,"temperature":99,"flow":9000}
                    masa cero                | {"mass":0,"density":0.9,"temperature":99,"flow":9000}
                    masa negativa            | {"mass":-1,"density":0.9,"temperature":99,"flow":9000}
                    caudal cero              | {"mass":200,"density":0.9,"temperature":99,"flow":0}
                    caudal negativo          | {"mass":200,"density":0.9,"temperature":99,"flow":-1}
                    """)
    void discardedReadingsLeaveHeaderAndHistoryUntouched(String description, String body)
            throws Exception {
        CircuitOrder order = orderAt(Stage.LOADING);
        List<Object> before = snapshot(order.number());

        JsonNode response = send(order.number(), "readings", body, 200);

        assertThat(response.get("accepted").asBoolean()).isFalse();
        assertThat(response.get("stored").asBoolean()).isFalse();
        assertThat(response.get("reason").asText()).startsWith("Lectura descartada");
        assertThat(snapshot(order.number())).isEqualTo(before);
    }

    @Test
    void discardedFirstReadingDoesNotStartTheLoad() throws Exception {
        CircuitOrder order = orderAt(Stage.ACTIVATED);

        send(order.number(), "readings", reading(0, 0.5, 20, 1000), 200);

        assertThat(order(order.number()).getStartedAt()).isNull();
        // Como la carga no empezó, el surtidor todavía se puede volver a activar.
        send(order.number(), "activation", passwordBody(order.password()), 200);
    }

    // ---------------------------------------------------------------------------------------------
    // Concurrencia
    // ---------------------------------------------------------------------------------------------

    @Test
    void concurrentTaresProduceOneTransition() throws Exception {
        long number = orderAt(Stage.PENDING).number();
        Callable<Boolean> tare =
                () -> {
                    try {
                        orders.registerTare(number, TARE);
                        return true;
                    } catch (InvalidStateException ex) {
                        return false;
                    }
                };

        assertThat(race(tare, tare)).containsExactlyInAnyOrder(true, false);
        assertThat(transitionsOf(number)).filteredOn(t -> t.getToState() == 2).hasSize(1);
    }

    @Test
    void concurrentFinalWeightsProduceOneTransition() throws Exception {
        long number = orderAt(Stage.CLOSED).number();
        Callable<Boolean> finalWeight =
                () -> {
                    try {
                        orders.finalWeight(number, 8200, null);
                        return true;
                    } catch (InvalidStateException ex) {
                        return false;
                    }
                };

        assertThat(race(finalWeight, finalWeight)).containsExactlyInAnyOrder(true, false);
        assertThat(transitionsOf(number)).filteredOn(t -> t.getToState() == 4).hasSize(1);
    }

    /**
     * Las lecturas compiten con el cierre: la cabecera tiene que quedar con la última lectura
     * aceptada antes del cierre, y ninguna lectura posterior puede modificarla.
     */
    @Test
    void readingsRacingWithClosureNeverEnterAfterIt() throws Exception {
        long number = orderAt(Stage.ACTIVATED).number();
        CountDownLatch loading = new CountDownLatch(3);
        List<Double> accepted = new ArrayList<>();
        List<Double> rejected = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var reader =
                    executor.submit(
                            () -> {
                                // Lee hasta que el cierre la rechace; el tope evita colgar la prueba.
                                for (int mass = 1; mass <= 100_000; mass++) {
                                    try {
                                        orders.registerReading(number, readingRequest(mass));
                                        accepted.add((double) mass);
                                        loading.countDown();
                                    } catch (InvalidStateException ex) {
                                        rejected.add((double) mass);
                                        return null;
                                    }
                                }
                                throw new AssertionError("El cierre nunca rechazó una lectura");
                            });
            var closer =
                    executor.submit(
                            () -> {
                                assertThat(loading.await(30, TimeUnit.SECONDS)).isTrue();
                                orders.close(number, "Cierre durante la carga");
                                return null;
                            });
            closer.get(60, TimeUnit.SECONDS);
            reader.get(60, TimeUnit.SECONDS);
        }

        Order closed = order(number);
        assertThat(closed.getState()).isEqualTo(3);
        assertThat(accepted).hasSizeGreaterThanOrEqualTo(3);
        assertThat(closed.getLastMass()).isEqualTo(accepted.getLast());
        assertThat(rejected).singleElement().isEqualTo(accepted.getLast() + 1);
        assertThat(transitionsOf(number)).filteredOn(t -> t.getToState() == 3).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------------------------------------

    /** Un reinicio de la aplicación no pierde nada: se reabre el contexto sobre la misma base. */
    @Test
    void finishedOrderSurvivesAnApplicationRestart() throws Exception {
        long number = orderAt(Stage.FINISHED).number();
        ReconciliationResponse before = orders.reconciliation(number);
        Instant finishedAt = order(number).getFinishedAt();

        try (var restarted =
                new SpringApplicationBuilder(Application.class)
                        .web(WebApplicationType.NONE)
                        // Como argumentos: properties() no le ganaría a application.properties.
                        .run(
                                "--spring.datasource.url=" + DB_URL,
                                "--spring.datasource.username=" + DB_USER,
                                "--spring.datasource.password=",
                                "--spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP",
                                "--spring.main.banner-mode=off")) {
            assertThat(restarted.getBean(Flyway.class).info().pending()).isEmpty();
            IOrderBusiness business = restarted.getBean(IOrderBusiness.class);

            assertThat(business.reconciliation(number)).isEqualTo(before);
            Order reloaded = business.loadByOrderNumber(number);
            assertThat(reloaded.getState()).isEqualTo(4);
            assertThat(reloaded.getFinishedAt()).isEqualTo(finishedAt);
            assertThat(reloaded.getTruck().getPlate()).isEqualTo(plate(number));
            assertThat(business.readings(number)).hasSize(1);
        }
        assertAudited(order(number), Map.of());
    }

    // ---------------------------------------------------------------------------------------------
    // Ayudas
    // ---------------------------------------------------------------------------------------------

    /** Deja una orden nueva en el momento pedido usando solo la API, como un sistema externo. */
    private CircuitOrder orderAt(Stage stage) throws Exception {
        long number = NUMBERS.incrementAndGet();
        mvc.perform(json(post("/api/v1/orders"), sapOrder(number, "SAP-" + number)))
                .andExpect(status().isCreated());
        if (stage == Stage.PENDING) {
            return new CircuitOrder(number, "12345");
        }
        String password = send(number, "tare", "{\"tare\":8000}", 200).get("password").asText();
        if (stage != Stage.WEIGHED) {
            send(number, "activation", passwordBody(password), 200);
        }
        if (stage == Stage.LOADING || stage == Stage.CLOSED || stage == Stage.FINISHED) {
            send(number, "readings", READING_BODY, 200);
        }
        if (stage == Stage.CLOSED || stage == Stage.FINISHED) {
            send(number, "close", "{}", 200);
        }
        if (stage == Stage.FINISHED) {
            send(number, "final-weight", "{\"weight\":8100}", 200);
        }
        return new CircuitOrder(number, password);
    }

    private String sapOrder(long number, String externalCode) {
        // Sin código de orden tampoco se mandan códigos de maestros: se sincronizan por dato natural.
        String code = externalCode == null ? "" : "\"externalCode\":\"%s\",".formatted(externalCode);
        String master = externalCode == null ? "" : "\"externalCode\":\"M-%d\",".formatted(number);
        return """
                {%s"orderNumber":%d,"scheduledAt":"2026-11-10T10:00:00Z","preset":15000,
                 "truck":{%s"plate":"%s","description":"Scania R450","tanks":[10000,5000]},
                 "driver":{%s"name":"Ana","lastName":"Pérez","document":"%d"},
                 "customer":{%s"name":"Gas del Sur %d","contact":"Compras"},
                 "product":{%s"name":"GLP %d","description":"Gas licuado de petróleo"}}
                """
                .formatted(
                        code, number, master, plate(number), master, 30_000_000 + number, master,
                        number, master, number);
    }

    private static String plate(long number) {
        return "AJ%03dKL".formatted(number % 1000);
    }

    private static String passwordBody(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    private static String reading(double mass, double density, double temperature, double flow) {
        return "{\"mass\":%s,\"density\":%s,\"temperature\":%s,\"flow\":%s}"
                .formatted(mass, density, temperature, flow);
    }

    private static ReadingRequest readingRequest(double mass) {
        ReadingRequest request = new ReadingRequest();
        request.setMass(mass);
        request.setDensity(0.55);
        request.setTemperature(20.0);
        request.setFlow(1000.0);
        return request;
    }

    /** Cuerpo correcto para cada operación: si se rechaza es por el momento, no por los datos. */
    private static String validBody(CircuitOrder order, String operation) {
        return switch (operation) {
            case "tare" -> "{\"tare\":8000}";
            case "activation" -> passwordBody(order.password());
            case "readings" -> "{\"mass\":5000,\"density\":0.55,\"temperature\":20,\"flow\":1000}";
            case "close" -> "{}";
            case "final-weight" -> "{\"weight\":8200}";
            default -> "";
        };
    }

    private JsonNode perform(CircuitOrder order, String operation, String body, int expected)
            throws Exception {
        return switch (operation) {
            case "reconciliation" -> read("/api/v1/orders/" + order.number() + "/reconciliation", expected);
            case "history" -> read("/api/v1/orders/" + order.number() + "/readings", expected);
            case "order" -> read("/api/v1/orders/" + order.number(), expected);
            default -> send(order.number(), operation, body, expected);
        };
    }

    private JsonNode send(long number, String operation, String body, int expected) throws Exception {
        return parse(
                mvc.perform(json(post("/api/v1/orders/{n}/{op}", number, operation), body))
                        .andExpect(status().is(expected)));
    }

    private JsonNode read(String url, int expected) throws Exception {
        return parse(mvc.perform(get(url)).andExpect(status().is(expected)));
    }

    private JsonNode parse(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Order order(long number) {
        return orderRepository.findByOrderNumber(number).orElseThrow();
    }

    private List<StateTransition> transitionsOf(long number) {
        return transitions.findByOrderIdOrderByOccurredAtAsc(order(number).getId());
    }

    /** Todo lo que una operación rechazada no debería tocar, incluida la versión de la fila. */
    private List<Object> snapshot(long number) {
        Order order = order(number);
        return Arrays.asList(
                order.getState(),
                order.getVersion(),
                order.getTare(),
                order.getFinalWeight(),
                order.getLastMass(),
                order.getLastDensity(),
                order.getLastTemperature(),
                order.getLastFlow(),
                order.getActivatedAt(),
                order.getStartedAt(),
                order.getFinishedAt(),
                order.getLastReceivedAt(),
                order.getLastStoredAt(),
                order.getFinalWeighedAt(),
                order.getActivationHash(),
                transitions.findByOrderIdOrderByOccurredAtAsc(order.getId()).size(),
                details.findByOrderIdOrderByReceivedAtAsc(order.getId()).size());
    }

    /** Todos los errores de la API responden con el mismo formato. */
    private static void assertStandardError(JsonNode error, int code) {
        assertThat(error.get("code").asInt()).isEqualTo(code);
        assertThat(error.get("error").asText()).isNotBlank();
        assertThat(error.get("message").asText()).isNotBlank();
        assertThat(error.get("timestamp").isTextual()).isTrue();
    }

    private void assertChronological(Order order) {
        List<Instant> dates =
                List.of(
                        order.getReceivedAt(),
                        order.getInitialWeighedAt(),
                        order.getActivatedAt(),
                        order.getStartedAt(),
                        order.getFinishedAt(),
                        order.getFinalWeighedAt());
        assertThat(dates).isSorted();
    }

    /** El log de auditoría tiene las cuatro transiciones, en orden, con su actor y su fecha. */
    private void assertAudited(Order order, Map<Integer, String> observations) {
        List<StateTransition> history =
                transitions.findByOrderIdOrderByOccurredAtAsc(order.getId());
        assertThat(history).extracting(StateTransition::getFromState).containsExactly(null, 1, 2, 3);
        assertThat(history).extracting(StateTransition::getToState).containsExactly(1, 2, 3, 4);
        assertThat(history)
                .extracting(StateTransition::getActor)
                .containsExactly("SAP", "TMS", "LOADER", "TMS");
        assertThat(history).allSatisfy(t -> assertThat(t.getOccurredAt()).isNotNull());
        observations.forEach(
                (state, text) ->
                        assertThat(history.get(state - 1).getObservation()).isEqualTo(text));
    }

    /** Arranca las dos tareas a la vez y devuelve sus resultados. */
    private static List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(synchronizedTask(first, ready, start));
            var secondResult = executor.submit(synchronizedTask(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private static Callable<Boolean> synchronizedTask(
            Callable<Boolean> task, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Se agotó la espera del inicio simultáneo");
            }
            return task.call();
        };
    }
}
