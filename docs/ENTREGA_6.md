# Entrega 6: cierre, pesaje final y conciliación

Cambios incrementales sobre `develop`, con las entregas 1 a 5 ya implementadas.
Se adaptó el comportamiento de la carpeta de referencia a la arquitectura actual:
se conservan las URLs por `orderNumber`, las excepciones y respuestas de error,
la activación repetible antes de la primera lectura y el muestreo existente.

| Operación | Solicitud | Resultado |
| --- | --- | --- |
| Cerrar carga | `POST /api/v1/orders/{orderNumber}/close` con `{"observation":"Carga completa"}` o `{}` | Estado 2 → 3; devuelve la orden e invalida el hash de activación. |
| Pesaje final | `POST /api/v1/orders/{orderNumber}/final-weight` con `{"weight":8250,"observation":"Balanza final"}` | Estado 3 → 4; guarda peso bruto y fecha, devuelve la conciliación. |
| Consultar conciliación | `GET /api/v1/orders/{orderNumber}/reconciliation` | Disponible únicamente en estado 4; no repite el pesaje ni genera transiciones. |

Las observaciones son opcionales y admiten hasta 255 caracteres. El peso final
es obligatorio, finito, mayor que cero, de hasta 1.000.000 kg y no menor que la
tara. La igualdad con la tara es válida. Los datos inválidos devuelven 400; una
orden inexistente, 404; un estado incorrecto o una operación repetida, 409.

Cierre, lecturas y pesaje final toman el mismo bloqueo pesimista sobre la orden,
retenido hasta terminar la transacción. Si una lectura obtiene el bloqueo antes,
termina antes del cierre; si lo obtiene después, encuentra estado 3 y devuelve
409. Dos cierres simultáneos producen un único cambio de estado y un conflicto.
Cada transición se audita dentro de la misma transacción: si la auditoría falla,
se deshace también el cambio en la orden. Se usan los actores `LOADER` para el
cierre y `TMS` para el pesaje final, siguiendo el esquema actual de actores por
sistema, sin incorporar autenticación de la referencia.

## Cálculo de conciliación

- `loadedProduct`: última masa acumulada aceptada de la cabecera (`lastMass`),
  incluso cuando esa lectura no se guardó en el historial por el muestreo.
- `netWeight`: peso bruto final menos tara.
- `difference`: neto de balanza menos producto cargado según el caudalímetro;
  conserva el signo.
- `averageTemperature`, `averageDensity` y `averageFlow`: promedios aritméticos
  calculados en la base sobre los detalles efectivamente almacenados.
- `unit`: `kg`, para los pesos y su diferencia. La densidad mantiene g/cm3,
  la temperatura °C y el caudal kg/h.

Como en la referencia, se permite cerrar sin lecturas: la masa cargada es cero y
los promedios son `null`. Se conserva `finishedAt` de la última lectura válida;
la fecha del cierre queda en su transición de auditoría. La respuesta incluye
además `id`, `externalCode`, `orderNumber`, `tare` y `finalWeight`.

## Verificación

Con Java 21 y Maven, desde `integrador/`:

```sh
mvn verify
```

`ClosureTest` cubre las nuevas rutas, estados inválidos, bloqueo de lecturas y
activación después del cierre, pesos inválidos sin mutaciones, igualdad con la
tara, cierre sin lecturas, masa de cabecera frente a historial muestreado,
promedios con varios detalles, consulta posterior consistente, concurrencia de
cierres, auditoría y rollback de ambas transiciones. Las pruebas usan H2 en modo
MySQL; no equivalen a una verificación de concurrencia contra un servidor MySQL.
Se ejecutan también las pruebas existentes como regresión de las entregas previas.

No se incorporan el frontend, JWT, alarmas, usuarios, infraestructura ni la suite
general del circuito o los artefactos de entrega de la referencia (entregas 7 y 8).
No se requieren nuevas migraciones: los campos y tablas ya existen.
