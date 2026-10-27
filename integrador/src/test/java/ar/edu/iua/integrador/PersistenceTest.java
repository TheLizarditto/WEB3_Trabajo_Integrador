package ar.edu.iua.integrador;

import ar.edu.iua.integrador.model.Customer;
import ar.edu.iua.integrador.model.Driver;
import ar.edu.iua.integrador.model.LoadDetail;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.Product;
import ar.edu.iua.integrador.model.StateTransition;
import ar.edu.iua.integrador.model.Truck;
import ar.edu.iua.integrador.model.persistence.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceTest {
    @Autowired private MasterRepository masters;
    @Autowired private TruckRepository trucks;
    @Autowired private OrderRepository orders;
    @Autowired private LoadDetailRepository readings;
    @Autowired private StateTransitionRepository transitions;
    @Autowired private EntityManager entityManager;

    private Order createOrder() {
        var truck = new Truck();
        truck.setName("Camión");
        truck.setPlate("AA123BB");
        truck.replaceCompartments(List.of(10000.0, 5000.0));
        trucks.save(truck);
        var driver = new Driver();
        driver.setName("Ana");
        masters.save(driver);
        var customer = new Customer();
        customer.setName("Cliente");
        masters.save(customer);
        var product = new Product();
        product.setName("GLP");
        masters.save(product);
        var order = new Order();
        order.setOrderNumber(501L);
        order.setTruck(truck);
        order.setDriver(driver);
        order.setCustomer(customer);
        order.setProduct(product);
        order.setState(1);
        order.setPreset(1000);
        order.setReceivedAt(Instant.parse("2026-10-27T12:00:00Z"));
        order.setScheduledAt(order.getReceivedAt());
        return orders.saveAndFlush(order);
    }

    @Test
    void persistsTypedOrderReadingsAndChronologicalHistory() {
        var order = createOrder();
        var first = order.getReceivedAt();
        for (int index : new int[] {2, 1}) {
            var reading = new LoadDetail();
            reading.setOrderId(order.getId());
            reading.setMass(index * 100);
            reading.setDensity(0.5);
            reading.setTemperature(index * 10);
            reading.setFlow(index * 1000);
            reading.setReceivedAt(first.plusSeconds(index));
            readings.save(reading);
            var transition = new StateTransition();
            transition.setOrderId(order.getId());
            transition.setFromState(index == 1 ? null : 1);
            transition.setToState(index);
            transition.setActor("operador");
            transition.setOccurredAt(first.plusSeconds(index));
            transitions.save(transition);
        }
        entityManager.flush();
        entityManager.clear();

        var reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getTruck().getCompartments()).containsExactly(10000.0, 5000.0);
        assertThat(reloaded.getTruck().getTotalLiters()).isEqualTo(15000);
        assertThat(reloaded.getDriver().getName()).isEqualTo("Ana");
        assertThat(reloaded.getProduct().getName()).isEqualTo("GLP");
        assertThat(reloaded.getCustomer().getName()).isEqualTo("Cliente");
        assertThat(readings.findByOrderIdOrderByReceivedAtAsc(order.getId()))
                .extracting(LoadDetail::getMass).containsExactly(100.0, 200.0);
        assertThat(readings.averages(order.getId())).singleElement()
                .satisfies(values -> assertThat(values).containsExactly(15.0, 0.5, 1500.0));
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(order.getId()))
                .extracting(StateTransition::getToState).containsExactly(1, 2);

        reloaded.getTruck().replaceCompartments(List.of(12000.0));
        trucks.saveAndFlush(reloaded.getTruck());
        entityManager.clear();
        assertThat(trucks.findById(reloaded.getTruck().getId()).orElseThrow().getCompartments())
                .containsExactly(12000.0);
    }

    @Test
    void rejectsReadingForMissingOrder() {
        var reading = new LoadDetail();
        reading.setOrderId(Long.MAX_VALUE);
        reading.setMass(100);
        reading.setDensity(0.5);
        reading.setFlow(1000);
        reading.setReceivedAt(Instant.now());
        assertThatThrownBy(() -> readings.saveAndFlush(reading))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permitsNullMasterCodesButRejectsDuplicateCodesWithinSameKind() {
        for (int index = 0; index < 2; index++) {
            var customer = new Customer();
            customer.setName("Cliente local " + index);
            masters.saveAndFlush(customer);
        }
        var customer = new Customer();
        customer.setName("Cliente integrado");
        customer.setExternalCode("EXT-1");
        masters.saveAndFlush(customer);
        var product = new Product();
        product.setName("Producto con igual código");
        product.setExternalCode("EXT-1");
        masters.saveAndFlush(product);
        var duplicate = new Customer();
        duplicate.setName("Cliente duplicado");
        duplicate.setExternalCode("EXT-1");
        assertThatThrownBy(() -> masters.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
