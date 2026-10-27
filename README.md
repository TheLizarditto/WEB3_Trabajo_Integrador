# Trabajo Integrador — Ingeniería Web 3

Backend para la administración de órdenes de carga de gas líquido.

## Entrega 1 — Base del backend

Primera entrega del integrante A, correspondiente al 27/10/2026. Incluye la
estructura Spring Boot/Maven, configuración por entorno, paquetes por
responsabilidad y un endpoint para comprobar que la aplicación está funcionando.

## Requisitos

- JDK 21.
- Maven 3.9 o superior.

## Compilar y ejecutar

Desde la raíz del repositorio:

```shell
cd integrador
mvn verify
java -jar target/integrador.jar
```

El perfil predeterminado es `dev` y el puerto predeterminado es `8080`.
Para comprobar el arranque, consultar:

```shell
curl http://localhost:8080/api/v1/health
```

Respuesta esperada, con estado HTTP `200`:

```json
{"status":"UP","application":"integrador"}
```

Este endpoint indica que el proceso web responde. Para arrancar la aplicación
ahora también es necesario configurar la base de datos indicada abajo.

## Entrega 2 — Modelo y persistencia

Se incorporan las entidades y repositorios del proyecto completo para maestros
(clientes, choferes, productos y camiones), cisternas, órdenes, lecturas
(`LoadDetail`) e historial de transiciones. Los maestros usan herencia JPA
`JOINED`; cada cisterna se identifica por camión y posición. Las órdenes tienen
control de versión y consultas con bloqueo para futuras operaciones de negocio.

Flyway aplica las migraciones de `src/main/resources/db/migration` al arrancar:
`V1` crea el esquema del alcance, `V2` permite códigos externos nulos y `V3`
separa los tipos de maestros conservando sus identificadores y relaciones.
Estas migraciones están adaptadas a esta entrega: no incluyen usuarios,
configuración de alarmas ni alarmas del proyecto completo. Hibernate valida el
esquema; no crea ni modifica tablas automáticamente.

Crear una base MySQL vacía y definir las credenciales antes de ejecutar:

```shell
export DB_URL='jdbc:mysql://localhost:3306/iw3_integrador'
export DB_USER='integrador'
export DB_PASSWORD='tu-clave-local'
java -jar target/integrador.jar
```

El usuario de la base debe poder crear y alterar tablas para aplicar las
migraciones. Usar una base propia para esta entrega, sin compartir el historial
Flyway con el proyecto completo, ya que el esquema inicial tiene otro alcance.
Las pruebas de persistencia usan H2 en modo MySQL, sin requerir un servidor
externo, y se ejecutan con `mvn verify`.

## Configuración por entorno

Spring Boot lee estas variables del entorno del proceso:

| Variable | Predeterminado | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Selecciona `dev` o `prod`. |
| `SERVER_PORT` | `8080` | Puerto HTTP de la aplicación. |
| `DB_URL` | Sin predeterminado | URL JDBC de MySQL. |
| `DB_USER` | Sin predeterminado | Usuario de la base de datos. |
| `DB_PASSWORD` | Sin predeterminado | Contraseña de la base de datos. |

También se pueden indicar al ejecutar el JAR:

```shell
java -jar target/integrador.jar --spring.profiles.active=prod --server.port=8081
```

`dev` habilita logs DEBUG del proyecto. `prod` usa INFO y oculta detalles internos
en las respuestas de error. Los archivos de configuración compartidos no contienen
credenciales. Las fechas se configuran para Argentina.

## Estructura

```text
integrador/
├── pom.xml
└── src/main/
    ├── java/ar/edu/iua/integrador/
    │   ├── Application.java
    │   ├── controllers/        Rutas y controladores REST
    │   ├── business/           Lógica de negocio
    │   │   └── exception/      Excepciones de negocio
    │   ├── model/              Entidades del dominio
    │   │   └── persistence/    Repositorios
    │   ├── dto/                Contratos de entrada y salida
    │   └── util/               Utilidades compartidas
    └── resources/
        ├── application.properties
        ├── application-dev.properties
        ├── application-prod.properties
        └── db/migration/       Migraciones SQL de Flyway
```

Los paquetes reservados para las próximas entregas se documentan mediante
`package-info.java`. El punto de entrada está en el paquete raíz para que Spring
detecte los componentes de sus subpaquetes.
