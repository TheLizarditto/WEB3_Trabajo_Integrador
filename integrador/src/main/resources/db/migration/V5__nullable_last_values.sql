-- Sin lecturas recibidas, los last_* no representan valores reales y se guardan como NULL.
ALTER TABLE iw3_order MODIFY COLUMN last_mass DOUBLE NULL;
ALTER TABLE iw3_order MODIFY COLUMN last_density DOUBLE NULL;
ALTER TABLE iw3_order MODIFY COLUMN last_temperature DOUBLE NULL;
ALTER TABLE iw3_order MODIFY COLUMN last_flow DOUBLE NULL;
UPDATE iw3_order SET last_mass = NULL, last_density = NULL, last_temperature = NULL, last_flow = NULL
 WHERE last_received_at IS NULL;
