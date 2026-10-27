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

En esta etapa se puede verificar el arranque y probar la persistencia. Los
paquetes de negocio y DTO quedan preparados para las próximas entregas; aún no
se exponen operaciones REST para administrar las entidades.

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
