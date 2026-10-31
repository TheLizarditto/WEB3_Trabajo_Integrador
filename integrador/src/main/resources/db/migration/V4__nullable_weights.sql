-- Los pesajes de balanza pasan a aceptar NULL: así se distingue "todavía no se pesó" de "pesó 0".
-- Las columnas last_* (lecturas del surtidor) quedan como están; se revisan con las lecturas.
ALTER TABLE iw3_order MODIFY COLUMN tare DOUBLE NULL;
ALTER TABLE iw3_order MODIFY COLUMN final_weight DOUBLE NULL;
-- Las órdenes ya guardadas sin pesaje tienen un 0 que no es un peso real.
UPDATE iw3_order SET tare = NULL WHERE initial_weighed_at IS NULL;
UPDATE iw3_order SET final_weight = NULL WHERE final_weighed_at IS NULL;
