package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.CustomerRequest;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.ProductRequest;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;

/**
 * Tara y activación del surtidor. Igual que OrderBusinessTest, NO es @Transactional: cada llamada
 * al negocio confirma su transacción y cada test usa su propio número de orden y patente.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tare;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
class TareActivationBusinessTest {
    @Autowired private IOrderBusiness orderBusiness;
    @Autowired private OrderRepository orders;
    @Autowired private StateTransitionRepository transitions;

    // Crea una orden en estado 1 recibida de SAP.
    private Order pendingOrder(long number, String plate) throws Exception {
        var truck = new TruckRequest();
        truck.setPlate(plate);
        truck.setTanks(List.of(10000.0, 5000.0));
        var driver = new DriverRequest();
        driver.setName("Ana");
        driver.setLastName("Pérez");
        var customer = new CustomerRequest();
        customer.setName("Gas del Sur SA");
        var product = new ProductRequest();
        product.setName("GLP");

        var request = new SapOrderRequest();
        request.setOrderNumber(number);
        request.setScheduledAt(Instant.parse("2026-11-05T10:00:00Z"));
        request.setPreset(15000.0);
        request.setTruck(truck);
        request.setDriver(driver);
        request.setCustomer(customer);
        request.setProduct(product);
        return orderBusiness.receiveFromSap(request);
    }

    // Una contraseña de 5 dígitos que seguro es distinta de la recibida.
    private String otherPassword(String password) {
        return String.format("%05d", (Integer.parseInt(password) + 1) % 100_000);
    }

    @Test
    void tareMovesOrderToStateTwoAndStoresOnlyTheHash() throws Exception {
        var created = pendingOrder(3001, "AD101EF");
        assertThat(created.getTare()).isNull();

        var response = orderBusiness.registerTare(3001, 8500);

        assertThat(response.getOrderNumber()).isEqualTo(3001);
        assertThat(response.getTare()).isEqualTo(8500);
        assertThat(response.getPassword()).matches("[0-9]{5}");

        var order = orderBusiness.loadByOrderNumber(3001);
        assertThat(order.getState()).isEqualTo(2);
        assertThat(order.getTare()).isEqualTo(8500);
        assertThat(order.getInitialWeighedAt()).isNotNull();
        assertThat(order.getActivationHash()).isNotEqualTo(response.getPassword());
        assertThat(new BCryptPasswordEncoder().matches(response.getPassword(), order.getActivationHash()))
                .isTrue();
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(order.getId()))
                .hasSize(2)
                .last()
                .satisfies(
                        transition -> {
                            assertThat(transition.getFromState()).isEqualTo(1);
                            assertThat(transition.getToState()).isEqualTo(2);
                            assertThat(transition.getActor()).isEqualTo("TMS");
                            assertThat(transition.getOccurredAt())
                                    .isEqualTo(order.getInitialWeighedAt());
                        });
    }

    @Test
    void secondTareIsInvalidStateAndKeepsTheFirstOne() throws Exception {
        pendingOrder(3002, "AD102EF");
        var first = orderBusiness.registerTare(3002, 8500);

        assertThatThrownBy(() -> orderBusiness.registerTare(3002, 9000))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("estado 2");

        var order = orderBusiness.loadByOrderNumber(3002);
        assertThat(order.getTare()).isEqualTo(8500);
        assertThat(new BCryptPasswordEncoder().matches(first.getPassword(), order.getActivationHash()))
                .isTrue();
    }

    @Test
    void tareOfMissingOrderIsNotFound() {
        assertThatThrownBy(() -> orderBusiness.registerTare(399999, 8500))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("399999");
    }

    @Test
    void activationReturnsPresetAndCanBeRepeatedKeepingTheFirstDate() throws Exception {
        pendingOrder(3003, "AD103EF");
        var password = orderBusiness.registerTare(3003, 8500).getPassword();

        var response = orderBusiness.activate(3003, password);
        assertThat(response.getOrderNumber()).isEqualTo(3003);
        assertThat(response.getPreset()).isEqualTo(15000);
        Instant firstActivation = orderBusiness.loadByOrderNumber(3003).getActivatedAt();
        assertThat(firstActivation).isNotNull();

        // El surtidor perdió la respuesta y vuelve a activarse: recibe el preset otra vez.
        assertThat(orderBusiness.activate(3003, password).getPreset()).isEqualTo(15000);
        var order = orderBusiness.loadByOrderNumber(3003);
        assertThat(order.getActivatedAt()).isEqualTo(firstActivation);
        assertThat(order.getState()).isEqualTo(2);
    }

    @Test
    void wrongPasswordIsRejectedAndDoesNotActivate() throws Exception {
        pendingOrder(3004, "AD104EF");
        var password = orderBusiness.registerTare(3004, 8500).getPassword();

        assertThatThrownBy(() -> orderBusiness.activate(3004, otherPassword(password)))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("3004");
        assertThat(orderBusiness.loadByOrderNumber(3004).getActivatedAt()).isNull();
    }

    @Test
    void activationBeforeTareIsInvalidState() throws Exception {
        pendingOrder(3005, "AD105EF");

        assertThatThrownBy(() -> orderBusiness.activate(3005, "12345"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("estado 1");
    }

    @Test
    void activationAfterLoadStartedIsInvalidState() throws Exception {
        pendingOrder(3006, "AD106EF");
        var password = orderBusiness.registerTare(3006, 8500).getPassword();
        orderBusiness.activate(3006, password);
        // Simula la primera lectura válida, que es la que completa startedAt.
        var order = orders.findByOrderNumber(3006L).orElseThrow();
        order.setStartedAt(Instant.now());
        orders.save(order);

        assertThatThrownBy(() -> orderBusiness.activate(3006, password))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("ya comenzó");
    }

    @Test
    void activationOfMissingOrderIsNotFound() {
        assertThatThrownBy(() -> orderBusiness.activate(399999, "12345"))
                .isInstanceOf(NotFoundException.class);
    }
}
