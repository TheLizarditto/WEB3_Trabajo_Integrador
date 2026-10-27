CREATE TABLE iw3_master (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(30) NOT NULL, external_code VARCHAR(100) NOT NULL,
 name VARCHAR(255) NOT NULL, last_name VARCHAR(255), document VARCHAR(255), plate VARCHAR(255),
 description VARCHAR(255), contact VARCHAR(255), CONSTRAINT uk_master_external UNIQUE(kind,external_code)
);
CREATE TABLE iw3_compartment (
 master_id BIGINT NOT NULL, position INTEGER NOT NULL, liters DOUBLE NOT NULL,
 PRIMARY KEY(master_id,position), FOREIGN KEY(master_id) REFERENCES iw3_master(id), CHECK(liters>0)
);
CREATE TABLE iw3_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_number BIGINT NOT NULL UNIQUE, external_code VARCHAR(100) NOT NULL UNIQUE,
 truck_id BIGINT NOT NULL, driver_id BIGINT NOT NULL, customer_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 state INTEGER NOT NULL, preset DOUBLE NOT NULL, tare DOUBLE NOT NULL, final_weight DOUBLE NOT NULL,
 last_mass DOUBLE NOT NULL, last_density DOUBLE NOT NULL, last_temperature DOUBLE NOT NULL, last_flow DOUBLE NOT NULL,
 received_at TIMESTAMP(6) NOT NULL, scheduled_at TIMESTAMP(6) NOT NULL, initial_weighed_at TIMESTAMP(6),
 activated_at TIMESTAMP(6), started_at TIMESTAMP(6), finished_at TIMESTAMP(6), final_weighed_at TIMESTAMP(6),
 last_received_at TIMESTAMP(6), last_stored_at TIMESTAMP(6), activation_hash VARCHAR(255), version BIGINT NOT NULL,
 FOREIGN KEY(truck_id) REFERENCES iw3_master(id), FOREIGN KEY(driver_id) REFERENCES iw3_master(id),
 FOREIGN KEY(customer_id) REFERENCES iw3_master(id), FOREIGN KEY(product_id) REFERENCES iw3_master(id),
 CHECK(state BETWEEN 1 AND 4), CHECK(preset>0)
);
CREATE TABLE iw3_detail (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, mass DOUBLE NOT NULL, density DOUBLE NOT NULL,
 temperature DOUBLE NOT NULL, flow DOUBLE NOT NULL, received_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES iw3_order(id), CHECK(mass>0), CHECK(flow>0), CHECK(density BETWEEN 0 AND 1)
);
CREATE INDEX idx_detail_order ON iw3_detail(order_id,received_at);
CREATE TABLE iw3_transition (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, from_state INTEGER, to_state INTEGER NOT NULL,
 actor VARCHAR(255) NOT NULL, observation VARCHAR(255), occurred_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES iw3_order(id)
);
