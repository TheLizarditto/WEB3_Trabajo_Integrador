package ar.edu.iua.integrador;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.iua.integrador.model.*;
import ar.edu.iua.integrador.model.persistence.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;

@DataJpaTest(
        properties = {
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class MigrationTest {
    private static final String URL = "jdbc:h2:mem:migration;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void migrateLegacyDatabase(DynamicPropertyRegistry properties) throws Exception {
        Flyway.configure()
                .dataSource(URL, "sa", "")
                .table("iw3_flyway_history")
                .target("2")
                .load()
                .migrate();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
INSERT INTO iw3_master(id,kind,external_code,name,plate,last_name,document,description,contact) VALUES
(101,'TRUCK','OLD-T','Camión','AA123BB',NULL,NULL,'Camión previo',NULL),
(102,'DRIVER',NULL,'Ana',NULL,'Pérez','12345678',NULL,NULL),
(103,'CUSTOMER','OLD-C','Cliente',NULL,NULL,NULL,NULL,'Contacto'),
(104,'PRODUCT','OLD-P','GLP',NULL,NULL,NULL,'Gas',NULL)
""");
            statement.executeUpdate(
                    "INSERT INTO iw3_compartment(master_id,position,liters)"
                            + " VALUES(101,0,10000),(101,1,5000)");
            statement.executeUpdate(
                    """
INSERT INTO iw3_order(id,order_number,external_code,truck_id,driver_id,customer_id,product_id,
state,preset,tare,final_weight,last_mass,last_density,last_temperature,last_flow,received_at,scheduled_at,version)
VALUES(201,501,NULL,101,102,103,104,1,1000,0,0,0,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
""");
            statement.executeUpdate(
                    """
INSERT INTO iw3_transition(order_id,from_state,to_state,actor,observation,occurred_at)
VALUES(201,NULL,1,'sap','Recepción anterior',CURRENT_TIMESTAMP)
""");
        }
        properties.add("spring.datasource.url", () -> URL);
    }

    @Autowired private OrderRepository orderRepository;
    @Autowired private TruckRepository truckRepository;
    @Autowired private StateTransitionRepository transitionRepository;
    @Autowired private MasterRepository masterRepository;

    @Test
    void migrationPreservesIdsTypesRelationshipsAndAudit() {
        var order = orderRepository.findById(201L).orElseThrow();
        assertThat(order.getExternalCode()).isNull();
        assertThat(order.getTruck().getId()).isEqualTo(101);
        assertThat(order.getTruck().getPlate()).isEqualTo("AA123BB");
        assertThat(order.getTruck().getCompartments()).containsExactly(10000.0, 5000.0);
        assertThat(order.getDriver().getId()).isEqualTo(102);
        assertThat(order.getDriver().getLastName()).isEqualTo("Pérez");
        assertThat(order.getDriver().getExternalCode()).isNull();
        assertThat(order.getCustomer().getContact()).isEqualTo("Contacto");
        assertThat(order.getProduct().getDescription()).isEqualTo("Gas");
        assertThat(masterRepository.findById(101L).orElseThrow()).isInstanceOf(Truck.class);
        assertThat(transitionRepository.findByOrderIdOrderByOccurredAtAsc(201L))
                .singleElement()
                .satisfies(transition -> assertThat(transition.getActor()).isEqualTo("sap"));
    }

    @Test
    void migratedTruckCanUpdateAndRemoveTanksWithoutChangingItsIdentity() {
        var truck = truckRepository.findById(101L).orElseThrow();
        truck.replaceCompartments(java.util.List.of(12000.0));
        truckRepository.saveAndFlush(truck);
        assertThat(truck.getId()).isEqualTo(101);
        assertThat(truck.getTotalLiters()).isEqualTo(12000);
    }
}
