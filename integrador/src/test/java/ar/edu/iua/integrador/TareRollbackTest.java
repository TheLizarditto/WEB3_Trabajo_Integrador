package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.dto.CustomerRequest;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.ProductRequest;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;

/**
 * Si falla el registro de la transición, la tara no puede quedar a medias: la orden sigue en
 * estado 1, sin tara ni contraseña. Va en una clase aparte porque el spy cambia el contexto.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tareRollback;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
class TareRollbackTest {
    @Autowired private IOrderBusiness orderBusiness;
    @MockitoSpyBean private StateTransitionRepository transitions;

    @Test
    void failedTransitionUndoesTheTare() throws Exception {
        var truck = new TruckRequest();
        truck.setPlate("AD301EF");
        truck.setTanks(List.of(10000.0));
        var driver = new DriverRequest();
        driver.setName("Ana");
        driver.setLastName("Pérez");
        var customer = new CustomerRequest();
        customer.setName("Gas del Sur SA");
        var product = new ProductRequest();
        product.setName("GLP");
        var request = new SapOrderRequest();
        request.setOrderNumber(3301L);
        request.setScheduledAt(Instant.parse("2026-11-05T10:00:00Z"));
        request.setPreset(15000.0);
        request.setTruck(truck);
        request.setDriver(driver);
        request.setCustomer(customer);
        request.setProduct(product);
        orderBusiness.receiveFromSap(request);

        // A partir de acá, guardar una transición falla.
        doThrow(new IllegalStateException("falla simulada")).when(transitions).save(any());

        assertThatThrownBy(() -> orderBusiness.registerTare(3301, 8500))
                .hasMessage("falla simulada");

        var order = orderBusiness.loadByOrderNumber(3301);
        assertThat(order.getState()).isEqualTo(1);
        assertThat(order.getTare()).isNull();
        assertThat(order.getInitialWeighedAt()).isNull();
        assertThat(order.getActivationHash()).isNull();
    }
}
