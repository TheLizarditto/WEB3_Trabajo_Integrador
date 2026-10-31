package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.CustomerRequest;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.ProductRequest;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.Driver;
import ar.edu.iua.integrador.model.Truck;
import ar.edu.iua.integrador.model.persistence.DriverRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;
import ar.edu.iua.integrador.model.persistence.TruckRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

/**
 * Recepción de órdenes de SAP. Esta clase NO es @Transactional a propósito: así cada llamada al
 * negocio confirma o deshace su propia transacción, como en la aplicación real. Por eso cada test
 * usa números de orden y patentes distintos.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
class OrderBusinessTest {
    @Autowired private IOrderBusiness orderBusiness;
    @Autowired private StateTransitionRepository transitions;
    @Autowired private TruckRepository trucks;
    @Autowired private DriverRepository drivers;

    private SapOrderRequest request(long number, String plate, String document) {
        var truck = new TruckRequest();
        truck.setPlate(plate);
        truck.setDescription("Scania");
        truck.setTanks(List.of(10000.0, 5000.0));
        var driver = new DriverRequest();
        driver.setName("Ana");
        driver.setLastName("Pérez");
        driver.setDocument(document);
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
        return request;
    }

    @Test
    void receivesOrderInStateOneWithItsTransition() throws Exception {
        var order = orderBusiness.receiveFromSap(request(1001, "AB100CD", "30100100"));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getState()).isEqualTo(1);
        assertThat(order.getReceivedAt()).isNotNull();
        assertThat(order.getTruck().getPlate()).isEqualTo("AB100CD");
        assertThat(transitions.findByOrderIdOrderByOccurredAtAsc(order.getId()))
                .singleElement()
                .satisfies(
                        transition -> {
                            assertThat(transition.getFromState()).isNull();
                            assertThat(transition.getToState()).isEqualTo(1);
                            assertThat(transition.getActor()).isEqualTo("SAP");
                        });
        assertThat(orderBusiness.loadByOrderNumber(1001).getId()).isEqualTo(order.getId());
    }

    @Test
    void repeatedOrderNumberIsConflict() throws Exception {
        orderBusiness.receiveFromSap(request(1002, "AB200CD", "30200200"));

        assertThatThrownBy(() -> orderBusiness.receiveFromSap(request(1002, "AB201CD", "30200201")))
                .isInstanceOf(FoundException.class)
                .hasMessageContaining("1002");
    }

    @Test
    void conflictInOneMasterUndoesTheOthers() throws Exception {
        // Ya hay un camión y un chofer con documento 30300300 que tiene otro código externo.
        orderBusiness.receiveFromSap(request(1003, "AB300CD", "30300300"));
        Driver driver = drivers.findAllByDocumentOrderByIdAsc("30300300").getFirst();
        driver.setExternalCode("D-9");
        drivers.save(driver);

        // La orden nueva cambia la descripción del camión (se sincroniza primero) y después
        // choca con el chofer: el cambio del camión no tiene que quedar guardado.
        var request = request(1004, "AB300CD", "30300300");
        request.getTruck().setDescription("Descripción nueva");
        request.getDriver().setExternalCode("D-1");

        assertThatThrownBy(() -> orderBusiness.receiveFromSap(request))
                .isInstanceOf(FoundException.class);
        Truck truck = trucks.findAllByPlateOrderByIdAsc("AB300CD").getFirst();
        assertThat(truck.getDescription()).isEqualTo("Scania");
    }

    @Test
    void loadingMissingOrderIsNotFound() {
        assertThatThrownBy(() -> orderBusiness.loadByOrderNumber(999999))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999999");
    }
}
