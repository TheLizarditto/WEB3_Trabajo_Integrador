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

## Modelo y migraciones

Los maestros comparten identidad y código externo mediante herencia JPA
`JOINED`, con una tabla específica para cada tipo. Cada cisterna tiene una clave
compuesta por camión y posición. Las órdenes incorporan control de versión y
los repositorios ofrecen consultas con bloqueo para futuras operaciones de
negocio.

Las migraciones se encuentran en
`integrador/src/main/resources/db/migration` y se ejecutan en este orden:

| Versión | Cambio |
|---|---|
| `V1` | Crea las tablas, relaciones y restricciones iniciales. |
| `V2` | Permite códigos externos nulos en maestros y órdenes. |
| `V3` | Separa los tipos de maestros conservando identificadores y relaciones. |
| `V4` | Permite tara y pesaje final nulos, para distinguir una orden sin pesar de un peso 0. |

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
    └── test/java/                  Pruebas de persistencia y migraciones
```

El punto de entrada está en el paquete raíz para que Spring detecte los
componentes de sus subpaquetes. Los paquetes reservados se documentan mediante
`package-info.java`.
