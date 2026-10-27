-- Keep the shared integration identity and every historical ID/foreign key.
-- JOINED inheritance moves each concept's attributes into its own table.
-- Legacy columns remain in iw3_master for comparison with the pre-migration data.
CREATE TABLE iw3_truck (
 id BIGINT PRIMARY KEY, plate VARCHAR(255), description VARCHAR(255),
 FOREIGN KEY(id) REFERENCES iw3_master(id)
);
CREATE TABLE iw3_driver (
 id BIGINT PRIMARY KEY, last_name VARCHAR(255), document VARCHAR(255),
 FOREIGN KEY(id) REFERENCES iw3_master(id)
);
CREATE TABLE iw3_customer (
 id BIGINT PRIMARY KEY, contact VARCHAR(255),
 FOREIGN KEY(id) REFERENCES iw3_master(id)
);
CREATE TABLE iw3_product (
 id BIGINT PRIMARY KEY, description VARCHAR(255),
 FOREIGN KEY(id) REFERENCES iw3_master(id)
);
INSERT INTO iw3_truck(id,plate,description)
 SELECT id,plate,description FROM iw3_master WHERE kind='TRUCK';
INSERT INTO iw3_driver(id,last_name,document)
 SELECT id,last_name,document FROM iw3_master WHERE kind='DRIVER';
INSERT INTO iw3_customer(id,contact)
 SELECT id,contact FROM iw3_master WHERE kind='CUSTOMER';
INSERT INTO iw3_product(id,description)
 SELECT id,description FROM iw3_master WHERE kind='PRODUCT';
-- Enforce the concrete type as well as the original shared identity.
ALTER TABLE iw3_order ADD CONSTRAINT fk_order_truck_type FOREIGN KEY(truck_id) REFERENCES iw3_truck(id);
ALTER TABLE iw3_order ADD CONSTRAINT fk_order_driver_type FOREIGN KEY(driver_id) REFERENCES iw3_driver(id);
ALTER TABLE iw3_order ADD CONSTRAINT fk_order_customer_type FOREIGN KEY(customer_id) REFERENCES iw3_customer(id);
ALTER TABLE iw3_order ADD CONSTRAINT fk_order_product_type FOREIGN KEY(product_id) REFERENCES iw3_product(id);
ALTER TABLE iw3_compartment ADD CONSTRAINT fk_compartment_truck_type FOREIGN KEY(master_id) REFERENCES iw3_truck(id);
