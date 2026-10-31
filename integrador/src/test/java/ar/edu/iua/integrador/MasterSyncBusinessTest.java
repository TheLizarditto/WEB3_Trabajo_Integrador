package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.iua.integrador.business.IMasterSyncBusiness;
import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.Truck;
import ar.edu.iua.integrador.model.persistence.TruckRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Casos de la sincronización de maestros, con el camión como ejemplo. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:sync;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
// Cada test corre en una transacción que se deshace al terminar, así no se pisan entre ellos.
@Transactional
class MasterSyncBusinessTest {
    @Autowired private IMasterSyncBusiness sync;
    @Autowired private TruckRepository trucks;

    private Truck existingTruck(String code, String plate) {
        var truck = new Truck();
        truck.setExternalCode(code);
        truck.setName(plate);
        truck.setPlate(plate);
        truck.setDescription("Descripción vieja");
        truck.replaceCompartments(List.of(8000.0));
        return trucks.saveAndFlush(truck);
    }

    private TruckRequest request(String code, String plate) {
        var request = new TruckRequest();
        request.setExternalCode(code);
        request.setPlate(plate);
        request.setDescription("Scania");
        request.setTanks(List.of(10000.0, 5000.0));
        return request;
    }

    @Test
    void withCodeThatExistsUpdatesThatTruck() throws Exception {
        var existing = existingTruck("T-1", "AA123BB");

        var result = sync.syncTruck(request("T-1", "AA123BB"));

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getDescription()).isEqualTo("Scania");
        assertThat(result.getCompartments()).containsExactly(10000.0, 5000.0);
    }

    @Test
    void withNewCodeFoundByPlateWithoutCodeAssignsTheCode() throws Exception {
        var existing = existingTruck(null, "AA123BB");

        var result = sync.syncTruck(request("T-1", "AA123BB"));

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getExternalCode()).isEqualTo("T-1");
    }

    @Test
    void withNewCodeFoundByPlateWithOtherCodeIsConflictAndChangesNothing() {
        var existing = existingTruck("T-9", "AA123BB");

        assertThatThrownBy(() -> sync.syncTruck(request("T-1", "AA123BB")))
                .isInstanceOf(FoundException.class)
                .hasMessageContaining("T-9");
        var reloaded = trucks.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getExternalCode()).isEqualTo("T-9");
        assertThat(reloaded.getDescription()).isEqualTo("Descripción vieja");
        assertThat(reloaded.getCompartments()).containsExactly(8000.0);
    }

    @Test
    void withoutCodeFoundByPlateUpdatesThatTruck() throws Exception {
        var existing = existingTruck("T-1", "AA123BB");

        var result = sync.syncTruck(request(null, "AA123BB"));

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getExternalCode()).isEqualTo("T-1");
        assertThat(result.getDescription()).isEqualTo("Scania");
    }

    @Test
    void notFoundAnywhereCreatesANewTruck() throws Exception {
        var result = sync.syncTruck(request("T-1", "AA123BB"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("AA123BB");
        assertThat(trucks.count()).isEqualTo(1);
    }

    @Test
    void missingOptionalFieldKeepsThePreviousValue() throws Exception {
        existingTruck("T-1", "AA123BB");
        var request = request("T-1", "AA123BB");
        request.setDescription(null);

        var result = sync.syncTruck(request);

        assertThat(result.getDescription()).isEqualTo("Descripción vieja");
    }

    @Test
    void driverWithoutCodeNorDocumentIsAlwaysCreated() throws Exception {
        var request = new DriverRequest();
        request.setName("Ana");
        request.setLastName("Pérez");

        var first = sync.syncDriver(request);
        var second = sync.syncDriver(request);

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
