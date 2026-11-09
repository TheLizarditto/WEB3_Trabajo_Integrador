# Trabajo Integrador — Ingeniería Web 3

Backend para administrar órdenes de carga de gas líquido, desarrollado con
Java 21 y Spring Boot. La implementación se organiza por capas y avanza mediante
entregas incrementales.

## Estado actual

La primera entrega establece la estructura Maven, los perfiles de configuración
y un endpoint de salud. La segunda incorpora el modelo del dominio, los
repositorios JPA y las migraciones de la base de datos.

| Componente | Implementación |
|---|---|
| Maestros | Clientes, choferes, productos y camiones. |
| Cisternas | Capacidades por camión y posición. |
| Órdenes | Relaciones con los maestros, fechas, pesos y estado. |
| Lecturas | Registro de masa, densidad, temperatura y caudal. |
| Transiciones | Historial de estados con fecha, actor y observación. |

La tercera incorpora la recepción de órdenes desde SAP, con sincronización de
maestros, validaciones y documentación Swagger (ver
[Endpoints disponibles](#endpoints-disponibles)).

La cuarta incorpora el registro de la tara (pesaje inicial), con la generación
de la contraseña de activación, y la activación del surtidor, que devuelve el
preset.

La quinta incorpora el procesamiento de lecturas de carga, con descarte de
lecturas inválidas y un historial muestreado. La sexta incorpora el cierre de la
carga, el pesaje final y la conciliación. La séptima incorpora la prueba del
circuito completo (`CircuitTest`) y la colección de Postman.

Con estas entregas, una orden puede recorrer los cuatro estados a través de la
API.

## Requisitos

- JDK 21.
- Maven 3.9 o superior.
- MySQL para ejecutar la aplicación.

Las pruebas automatizadas utilizan H2 en modo MySQL y no necesitan un servidor
de base de datos externo.

## Compilación y pruebas

Desde la raíz del repositorio:

```shell
cd integrador
mvn verify
```

El comando compila, ejecuta las pruebas y genera `target/integrador.jar`. Las
pruebas verifican las relaciones entre entidades, el registro de lecturas y
transiciones, las restricciones de integridad y la conservación de datos al
aplicar migraciones.

Para ejecutar solamente las pruebas:

```shell
mvn test
```

### Circuito completo

`CircuitTest` recorre el circuito de una orden a través de la API (estados 1 a 4),
con la matriz de errores y transiciones, la auditoría, la concurrencia y la
persistencia tras un reinicio. Se ejecuta con `mvn verify`, sobre H2.

La colección de `postman/` prueba todos los endpoints contra la aplicación en
ejecución. En Postman, importar los dos archivos, seleccionar el entorno y usar
el Collection Runner; las carpetas de circuito se ejecutan en orden. También se
puede ejecutar con Newman desde la raíz del repositorio:

```shell
npx newman run postman/Integrador.postman_collection.json \
  -e postman/Integrador.postman_environment.json
```

Cada ejecución crea órdenes nuevas y reutiliza maestros con el prefijo `POSTMAN`.
El entorno apunta a `http://localhost:8080`; se puede cambiar `baseUrl` para
probar otra instalación.

La colección se ejecutó con Newman contra MySQL 8.4, sobre una base vacía: 61
solicitudes y 132 aserciones sin fallos, en dos corridas seguidas. Las pruebas de
concurrencia de `CircuitTest` y `ClosureTest` corren solo sobre H2; no equivalen
a una verificación de concurrencia contra un servidor MySQL.

## Configuración de la base de datos

Crear una base MySQL vacía y un usuario con permisos para crear y alterar tablas,
así como para consultar y modificar datos. La aplicación utiliza una base
exclusiva; Flyway registra allí las migraciones aplicadas.

Definir estas variables en la terminal donde se ejecutará el backend:

```shell
export DB_URL='jdbc:mysql://localhost:3306/iw3_integrador'
export DB_USER='integrador'
export DB_PASSWORD='tu-clave-local'
```

Los valores del ejemplo deben coincidir con la base y el usuario creados. Las
credenciales se leen desde el entorno y no se guardan en los archivos de
configuración del repositorio.

## Ejecución

Desde `integrador/`, después de compilar y configurar la conexión:

```shell
java -jar target/integrador.jar
```

El perfil predeterminado es `dev` y el puerto HTTP es `8080`. Al iniciar, Flyway
aplica las migraciones pendientes y Hibernate valida que el esquema corresponda
con las entidades.

Para comprobar que el proceso web responde, consultar desde otra terminal:

```shell
curl http://localhost:8080/api/v1/health
```

La respuesta esperada tiene estado HTTP `200`:

```json
{"status":"UP","application":"integrador"}
```

El backend se detiene con `Ctrl+C`. Los datos persistidos permanecen en MySQL
para el siguiente arranque.

## Endpoints disponibles

| Método | URL | Qué hace | Respuestas |
|---|---|---|---|
| `GET` | `/api/v1/health` | Indica que el proceso web responde. | `200` |
| `POST` | `/api/v1/orders` | Recibe una orden de SAP, sincroniza camión, chofer, cliente y producto, y la crea en estado 1. | `201` con header `Location`, `400` datos inválidos, `409` número o código de orden repetido, o maestro que ya tiene otro código externo |
| `GET` | `/api/v1/orders/{orderNumber}` | Devuelve la orden con ese número de orden. | `200`, `404` si no existe |
| `POST` | `/api/v1/orders/{orderNumber}/tare` | Registra la tara que envía el TMS, genera la contraseña de activación y pasa la orden a estado 2. | `200` con la contraseña, `400` tara inválida, `404` si no existe, `409` si la orden no está en estado 1 |
| `POST` | `/api/v1/orders/{orderNumber}/activation` | Valida la contraseña del surtidor y devuelve el preset. | `200` con el preset, `400` contraseña sin 5 dígitos, `403` contraseña incorrecta, `404` si no existe, `409` si la orden no está en estado 2 o la carga ya comenzó |
| `POST` | `/api/v1/orders/{orderNumber}/readings` | Procesa una lectura del surtidor. | `200` con `accepted` en `true` o `false` si se descarta, `400` datos faltantes o inválidos, `404` si no existe, `409` si la orden no está activada o no está en estado 2 |
| `GET` | `/api/v1/orders/{orderNumber}/readings` | Devuelve el historial muestreado de lecturas. | `200`, `404` si no existe |
| `POST` | `/api/v1/orders/{orderNumber}/close` | Cierra la carga y pasa la orden a estado 3. | `200` con la orden, `400` observación inválida, `404` si no existe, `409` si la orden no está en estado 2 |
| `POST` | `/api/v1/orders/{orderNumber}/final-weight` | Registra el peso final, pasa la orden a estado 4 y devuelve la conciliación. | `200` con la conciliación, `400` peso u observación inválidos, `404` si no existe, `409` si la orden no está en estado 3 |
| `GET` | `/api/v1/orders/{orderNumber}/reconciliation` | Devuelve la conciliación de una orden finalizada. | `200`, `404` si no existe, `409` si la orden no está en estado 4 |

Las URLs de órdenes usan el **número de orden** que envía SAP, no el `id`
interno de la base. El header `Location` del `POST` sigue el mismo formato, por
ejemplo `/api/v1/orders/501`.

Los errores se manejan de forma centralizada en `RestExceptionHandler` y se
devuelven con el formato `StandartResponse`; los nuevos controllers no necesitan
`try/catch`.

## Cómo probar en local

1. Levantar MySQL en Docker, reemplazando `<clave-root>` y `<clave-app>` por
   claves propias:

   ```shell
   docker run -d --name iw3-mysql \
     -e MYSQL_ROOT_PASSWORD='<clave-root>' \
     -e MYSQL_DATABASE=iw3_integrador \
     -e MYSQL_USER=integrador \
     -e MYSQL_PASSWORD='<clave-app>' \
     -p 3306:3306 \
     mysql:8.4
   ```

2. Compilar y definir la conexión desde `integrador/`:

   ```shell
   mvn clean package
   export DB_URL='jdbc:mysql://localhost:3306/iw3_integrador'
   export DB_USER='integrador'
   export DB_PASSWORD='<clave-app>'
   ```

   Si la conexión falla con `Public Key Retrieval is not allowed`, agregar
   `?allowPublicKeyRetrieval=true&useSSL=false` al final de `DB_URL`.

3. Arrancar con `java -jar target/integrador.jar` y abrir Swagger en
   <http://localhost:8080/swagger-ui.html>.

Ejemplo válido para `POST /api/v1/orders`:

```json
{
  "orderNumber": 501,
  "externalCode": "SAP-501",
  "scheduledAt": "2026-11-05T10:00:00Z",
  "preset": 15000,
  "truck": {
    "externalCode": "T-1",
    "plate": "AA123BB",
    "description": "Scania R450",
    "tanks": [10000, 5000]
  },
  "driver": {
    "externalCode": "D-1",
    "name": "Ana",
    "lastName": "Pérez",
    "document": "30111222"
  },
  "customer": {
    "externalCode": "C-1",
    "name": "Gas del Sur SA",
    "contact": "Compras"
  },
  "product": {
    "externalCode": "P-1",
    "name": "GLP",
    "description": "Gas licuado de petróleo"
  }
}
```

La patente debe estar en mayúsculas y sin espacios, con formato `AAA123` o
`AA123BB`. El documento es opcional y, si se envía, debe tener 7 u 8 dígitos sin
puntos. Los códigos externos son opcionales. `scheduledAt` debe incluir la zona
horaria (por ejemplo `Z` o `-03:00`).

Con la orden en estado 1, registrar la tara (peso del camión vacío, en kg):

```shell
curl -X POST http://localhost:8080/api/v1/orders/501/tare \
  -H 'Content-Type: application/json' \
  -d '{"tare": 8500}'
```

La respuesta incluye la contraseña de activación de 5 dígitos, por ejemplo
`{"orderNumber":501,"tare":8500.0,"password":"04217"}`. Es la única vez que se
devuelve: en la base se guarda solo su hash BCrypt y no aparece en la consulta
de la orden.

Con esa contraseña, activar el surtidor:

```shell
curl -X POST http://localhost:8080/api/v1/orders/501/activation \
  -H 'Content-Type: application/json' \
  -d '{"password": "04217"}'
```

La respuesta devuelve el preset: `{"orderNumber":501,"preset":15000.0}`. La
activación se puede repetir con la misma contraseña mientras no haya empezado la
carga, por si el surtidor no recibió la respuesta; se conserva la fecha de la
primera activación.

## Lecturas de carga

El surtidor envía `POST /api/v1/orders/{orderNumber}/readings`. La orden debe
estar en estado 2 y activada; de lo contrario, se responde `409`.

```json
{"mass": 1200.0, "density": 0.55, "temperature": 24.5, "flow": 1800.0}
```

`mass` es la masa acumulada en kg, `density` se expresa en g/cm3, `temperature`
en °C y `flow` en kg/h. Los cuatro campos son obligatorios y la densidad debe
estar entre 0 y 1 g/cm3. Los campos faltantes, la densidad fuera de rango y los
valores no finitos se responden con `400`.

| Lectura | Respuesta | Efecto |
|---|---|---|
| Caudal o masa menor o igual a 0 | `200` con `accepted: false` | Se descarta: no cambia la orden ni el historial. |
| Masa menor que la acumulada anterior | `200` con `accepted: false` | Se descarta: no cambia la orden ni el historial. |
| Válida | `200` con `accepted: true` | Actualiza los últimos valores de la orden. |

La primera lectura válida completa `startedAt` y cada lectura válida actualiza
`finishedAt`. El historial guarda la primera lectura y luego una por intervalo; el
campo `stored` de la respuesta indica si esa lectura se guardó.
`GET /api/v1/orders/{orderNumber}/readings` devuelve ese historial.

## Cierre, pesaje final y conciliación

| Operación | Solicitud | Resultado |
|---|---|---|
| Cerrar la carga | `POST /api/v1/orders/{orderNumber}/close` con `{"observation":"Carga completa"}` o `{}` | Estado 2 → 3. Devuelve la orden e invalida el hash de activación. |
| Pesaje final | `POST /api/v1/orders/{orderNumber}/final-weight` con `{"weight":23500,"observation":"Balanza final"}` | Estado 3 → 4. Guarda el peso bruto y la fecha, y devuelve la conciliación. |
| Consultar la conciliación | `GET /api/v1/orders/{orderNumber}/reconciliation` | Solo en estado 4. No repite el pesaje ni genera transiciones. |

El cierre necesita un cuerpo JSON, aunque sea `{}`: sin cuerpo responde `400`. Las
observaciones son opcionales y admiten hasta 255 caracteres. El peso final es
obligatorio, finito, mayor que 0, de hasta 1.000.000 kg y no menor que la tara; la
igualdad con la tara es válida. Se puede cerrar sin lecturas: la masa cargada es 0
y los promedios son `null`. Se conserva `finishedAt` de la última lectura válida;
la fecha del cierre queda en su transición.

Cierre, lecturas y pesaje final toman el mismo bloqueo pesimista sobre la orden.
Si una lectura obtiene el bloqueo antes, termina antes del cierre; si lo obtiene
después, encuentra el estado 3 y recibe `409`. Cada transición se registra en la
misma transacción que el cambio de la orden, con el actor `LOADER` para el cierre
y `TMS` para el pesaje final.

Ejemplo ilustrativo de conciliación (los valores no provienen de una ejecución
real):

```json
{
  "id": 1,
  "externalCode": "SAP-501",
  "orderNumber": 501,
  "tare": 8500.0,
  "finalWeight": 23500.0,
  "loadedProduct": 14950.0,
  "netWeight": 15000.0,
  "difference": 50.0,
  "averageTemperature": 24.5,
  "averageDensity": 0.55,
  "averageFlow": 1800.0,
  "unit": "kg"
}
```

- `loadedProduct`: última masa aceptada de la orden, aunque esa lectura no se haya
  guardado en el historial.
- `netWeight`: peso final menos tara.
- `difference`: neto de balanza menos producto cargado; conserva el signo.
- `averageTemperature`, `averageDensity` y `averageFlow`: promedios de las lecturas
  guardadas en el historial, no de todas las recibidas.
- `unit`: unidad de los pesos y de la diferencia. La densidad se mantiene en g/cm3,
  la temperatura en °C y el caudal en kg/h.

## Qué se simula

El proyecto implementa solo el backend. No hay integración real con SAP, con el
TMS y su balanza, ni con el surtidor y su caudalímetro: sus mensajes se simulan
con solicitudes HTTP desde la colección de Postman, Swagger o `curl`.

Las API no tienen autenticación en esta etapa. El actor de cada transición lo fija
el endpoint (`SAP`, `TMS` o `LOADER`), sin verificar quién envía la solicitud.

## Perfiles y variables de entorno

| Variable | Predeterminado | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Selecciona el perfil `dev` o `prod`. |
| `SERVER_PORT` | `8080` | Puerto HTTP de la aplicación. |
| `DB_URL` | Obligatoria | URL JDBC de MySQL. |
| `DB_USER` | Obligatoria | Usuario de la base de datos. |
| `DB_PASSWORD` | Obligatoria | Contraseña de la base de datos. |

`dev` habilita logs DEBUG del proyecto. `prod` utiliza INFO. Las respuestas de
error ocultan los detalles internos en ambos perfiles. La conexión JDBC trabaja
con UTC y Jackson usa la zona horaria de Argentina.

También es posible elegir el perfil y el puerto al ejecutar el JAR:

```shell
java -jar target/integrador.jar --spring.profiles.active=prod --server.port=8081
```

La propiedad `integrador.readings.storage-interval-seconds` define cada cuántos
segundos se guarda una lectura en el historial. Vale `10` por defecto y se define
en `application.properties`; también se puede cambiar al ejecutar el JAR:

```shell
java -jar target/integrador.jar --integrador.readings.storage-interval-seconds=30
```

## Modelo y migraciones

Los maestros comparten identidad y código externo mediante herencia JPA
`JOINED`, con una tabla específica para cada tipo. Cada cisterna tiene una clave
compuesta por camión y posición. Las órdenes incorporan control de versión y
los repositorios ofrecen consultas con bloqueo, que usan la tara, la activación,
las lecturas, el cierre y el pesaje final.

Las migraciones se encuentran en
`integrador/src/main/resources/db/migration` y se ejecutan en este orden:

| Versión | Cambio |
|---|---|
| `V1` | Crea las tablas, relaciones y restricciones iniciales. |
| `V2` | Permite códigos externos nulos en maestros y órdenes. |
| `V3` | Separa los tipos de maestros conservando identificadores y relaciones. |
| `V4` | Permite tara y pesaje final nulos, para distinguir una orden sin pesar de un peso 0. |
| `V5` | Permite últimos valores de lectura nulos, para distinguir una orden sin lecturas de una lectura en 0. |

Hibernate usa `ddl-auto=validate`: los cambios de esquema se realizan mediante
migraciones SQL, sin creación ni actualización automática de tablas por JPA.

## Organización del código

```text
integrador/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/ar/edu/iua/integrador/
    │   │   ├── Application.java
    │   │   ├── controllers/        Rutas y controladores REST
    │   │   ├── business/           Lógica de negocio
    │   │   │   └── exception/      Excepciones de negocio
    │   │   ├── model/              Entidades del dominio
    │   │   │   └── persistence/    Repositorios JPA
    │   │   ├── dto/                Contratos de entrada y salida
    │   │   └── util/               Utilidades compartidas
    │   └── resources/
    │       ├── application.properties
    │       ├── application-dev.properties
    │       ├── application-prod.properties
    │       └── db/migration/       Migraciones SQL de Flyway
    └── test/java/                  Pruebas automatizadas, incluido el circuito completo
postman/                            Colección y entorno de Postman
```

El punto de entrada está en el paquete raíz para que Spring detecte los
componentes de sus subpaquetes. Los paquetes reservados se documentan mediante
`package-info.java`.
