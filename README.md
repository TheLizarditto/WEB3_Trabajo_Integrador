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

Este endpoint indica que el proceso web responde. La persistencia se incorporará
en la siguiente entrega.

## Configuración por entorno

Spring Boot lee estas variables del entorno del proceso:

| Variable | Predeterminado | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Selecciona `dev` o `prod`. |
| `SERVER_PORT` | `8080` | Puerto HTTP de la aplicación. |

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
        └── application-prod.properties
```

Los paquetes reservados para las próximas entregas se documentan mediante
`package-info.java`. El punto de entrada está en el paquete raíz para que Spring
detecte los componentes de sus subpaquetes.
