package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.dto.*;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.persistence.LoadDetailRepository;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reading-business;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP",
    "integrador.readings.storage-interval-seconds=3600"
})
class ReadingBusinessTest {
    @Autowired private IOrderBusiness orders;
    @Autowired private OrderRepository orderRepository;
    @Autowired private LoadDetailRepository details;

    private Order activeOrder(long number, String plate, String document) throws Exception {
        var truck = new TruckRequest(); truck.setPlate(plate); truck.setDescription("Scania"); truck.setTanks(List.of(10000.0));
        var driver = new DriverRequest(); driver.setName("Ana"); driver.setLastName("Perez"); driver.setDocument(document);
        var customer = new CustomerRequest(); customer.setName("Gas del Sur");
        var product = new ProductRequest(); product.setName("GLP");
        var sap = new SapOrderRequest(); sap.setOrderNumber(number); sap.setScheduledAt(Instant.parse("2026-11-05T10:00:00Z")); sap.setPreset(15000.0); sap.setTruck(truck); sap.setDriver(driver); sap.setCustomer(customer); sap.setProduct(product);
        Order order = orders.receiveFromSap(sap);
        TareResponse tare = orders.registerTare(number, 8000);
        orders.activate(number, tare.getPassword());
        return order;
    }

    private ReadingRequest reading(double mass, double density, double temperature, double flow) {
        var r = new ReadingRequest(); r.setMass(mass); r.setDensity(density); r.setTemperature(temperature); r.setFlow(flow); return r;
    }

    @Test
    void validReadingStartsLoadUpdatesHeaderAndCreatesHistory() throws Exception {
        Order created = activeOrder(4101, "AF101GH", "41011001");
        var result = orders.registerReading(4101, reading(100, .55, 25, 1200));
        assertThat(result.accepted()).isTrue();
        assertThat(result.stored()).isTrue();
        Order current = orderRepository.findByOrderNumber(4101L).orElseThrow();
        assertThat(current.getLastMass()).isEqualTo(100);
        assertThat(current.getLastDensity()).isEqualTo(.55);
        assertThat(current.getStartedAt()).isNotNull();
        assertThat(current.getStartedAt()).isEqualTo(current.getFinishedAt());
        assertThat(current.getLastReceivedAt()).isNotNull();
        assertThat(current.getLastStoredAt()).isNotNull();
        assertThat(details.findByOrderIdOrderByReceivedAtAsc(created.getId())).hasSize(1);
    }

    @Test
    void discardedReadingsDoNotChangeHeaderOrHistory() throws Exception {
        Order created = activeOrder(4102, "AF102GH", "41011002");
        orders.registerReading(4102, reading(100, .5, 25, 1200));
        Order before = orderRepository.findByOrderNumber(4102L).orElseThrow();
        var received = before.getLastReceivedAt(); var started = before.getStartedAt(); var finished = before.getFinishedAt(); var stored = before.getLastStoredAt();
        assertThat(orders.registerReading(4102, reading(90, .6, 99, 1200)).accepted()).isFalse();
        assertThat(orders.registerReading(4102, reading(0, .6, 99, 1200)).accepted()).isFalse();
        assertThat(orders.registerReading(4102, reading(110, .6, 99, 0)).accepted()).isFalse();
        Order after = orderRepository.findByOrderNumber(4102L).orElseThrow();
        assertThat(after.getLastMass()).isEqualTo(100);
        assertThat(after.getLastDensity()).isEqualTo(.5);
        assertThat(after.getLastReceivedAt()).isEqualTo(received);
        assertThat(after.getStartedAt()).isEqualTo(started);
        assertThat(after.getFinishedAt()).isEqualTo(finished);
        assertThat(after.getLastStoredAt()).isEqualTo(stored);
        assertThat(details.findByOrderIdOrderByReceivedAtAsc(created.getId())).hasSize(1);
    }

    @Test
    void everyAcceptedReadingUpdatesHeaderButHistoryIsSampled() throws Exception {
        Order created = activeOrder(4103, "AF103GH", "41011003");
        assertThat(orders.registerReading(4103, reading(100, .55, 25, 1200)).stored()).isTrue();
        var second = orders.registerReading(4103, reading(200, .56, 26, 1100));
        assertThat(second.accepted()).isTrue();
        assertThat(second.stored()).isFalse();
        assertThat(orderRepository.findByOrderNumber(4103L).orElseThrow().getLastMass()).isEqualTo(200);
        assertThat(details.findByOrderIdOrderByReceivedAtAsc(created.getId())).hasSize(1);
    }

    @Test
    void firstValidReadingClosesActivationWindowAndStateOneCannotRead() throws Exception {
        var truck = new TruckRequest(); truck.setPlate("AF104GH"); truck.setDescription("Scania"); truck.setTanks(List.of(10000.0));
        var driver = new DriverRequest(); driver.setName("Ana"); driver.setLastName("Perez"); driver.setDocument("41011004");
        var customer = new CustomerRequest(); customer.setName("Gas del Sur"); var product = new ProductRequest(); product.setName("GLP");
        var sap = new SapOrderRequest(); sap.setOrderNumber(4104L); sap.setScheduledAt(Instant.parse("2026-11-05T10:00:00Z")); sap.setPreset(15000.0); sap.setTruck(truck); sap.setDriver(driver); sap.setCustomer(customer); sap.setProduct(product);
        orders.receiveFromSap(sap);
        assertThatThrownBy(() -> orders.registerReading(4104, reading(10, .5, 25, 1000))).isInstanceOf(InvalidStateException.class);
        var tare = orders.registerTare(4104, 8000); orders.activate(4104, tare.getPassword());
        orders.registerReading(4104, reading(10, .5, 25, 1000));
        assertThatThrownBy(() -> orders.activate(4104, tare.getPassword())).isInstanceOf(InvalidStateException.class);
    }
}
