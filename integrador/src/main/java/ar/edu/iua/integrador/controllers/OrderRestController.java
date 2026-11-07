package ar.edu.iua.integrador.controllers;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidDataException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.CloseRequest;
import ar.edu.iua.integrador.dto.FinalWeightRequest;
import ar.edu.iua.integrador.dto.ReconciliationResponse;
import ar.edu.iua.integrador.dto.ActivationRequest;
import ar.edu.iua.integrador.dto.ReadingRequest;
import ar.edu.iua.integrador.dto.ReadingResponse;
import ar.edu.iua.integrador.dto.ActivationResponse;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TareRequest;
import ar.edu.iua.integrador.dto.TareResponse;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.LoadDetail;
import ar.edu.iua.integrador.util.StandartResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de órdenes de carga. Solo traduce HTTP ↔ negocio: no tiene try/catch porque las
 * excepciones las convierte en respuestas RestExceptionHandler.
 *
 * Las URLs identifican la orden por su número (/orders/{orderNumber}) y no por el id interno:
 * el número es lo que conocen SAP y el TMS, y los próximos endpoints (tara, lecturas, cierre)
 * van a seguir el mismo formato /orders/{orderNumber}/...
 */
@RestController
@RequestMapping(Constants.URL_ORDERS)
@RequiredArgsConstructor
@Tag(name = "Órdenes", description = "Recepción y consulta de órdenes de carga")
public class OrderRestController {

    private final IOrderBusiness orderBusiness;

    /** Recibe una orden de SAP y la da de alta en estado 1. Devuelve 201 con el header Location. */
    @Operation(
            summary = "Recibir una orden desde SAP",
            description =
                    "Crea la orden en estado 1 (pendiente de pesaje inicial) y sincroniza camión,"
                            + " chofer, cliente y producto: si no existen se crean, si existen se"
                            + " actualizan.")
    @ApiResponse(
            responseCode = "201",
            description = "Orden creada",
            headers = @Header(name = "Location", description = "URL de la orden creada"))
    @ApiResponse(
            responseCode = "400",
            description = "El JSON es inválido o no cumple las validaciones",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Ya existe una orden con ese número o código, o un maestro tiene otro código",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "Error interno",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @PostMapping
    public ResponseEntity<Order> receiveFromSap(@Valid @RequestBody SapOrderRequest request)
            throws FoundException {
        Order order = orderBusiness.receiveFromSap(request);
        URI location = URI.create(Constants.URL_ORDERS + "/" + order.getOrderNumber());
        return ResponseEntity.created(location).body(order);
    }

    /** Devuelve la orden con ese número de orden. */
    @Operation(summary = "Consultar una orden por número de orden")
    @ApiResponse(responseCode = "200", description = "Orden encontrada")
    @ApiResponse(
            responseCode = "404",
            description = "No existe una orden con ese número",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @GetMapping("/{orderNumber}")
    public Order load(
            @Parameter(description = "Número de orden (el que mandó SAP)", example = "501")
                    @PathVariable
                    long orderNumber)
            throws NotFoundException {
        return orderBusiness.loadByOrderNumber(orderNumber);
    }

    /** Registra la tara que manda el TMS y devuelve la contraseña de activación. */
    @Operation(
            summary = "Registrar la tara (pesaje inicial)",
            description =
                    "Guarda el peso del camión vacío, genera una contraseña de activación de 5"
                            + " dígitos y pasa la orden a estado 2. La contraseña solo se devuelve"
                            + " en esta respuesta: en la base se guarda su hash.")
    @ApiResponse(responseCode = "200", description = "Tara registrada")
    @ApiResponse(
            responseCode = "400",
            description = "La tara falta, no es un número o no es mayor a 0",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No existe una orden con ese número",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "La orden no está en estado 1 (por ejemplo, ya tiene tara)",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @PostMapping("/{orderNumber}/tare")
    public TareResponse registerTare(
            @Parameter(description = "Número de orden (el que mandó SAP)", example = "501")
                    @PathVariable
                    long orderNumber,
            @Valid @RequestBody TareRequest request)
            throws NotFoundException, InvalidStateException {
        return orderBusiness.registerTare(orderNumber, request.getTare());
    }

    @Operation(
            summary = "Registrar una lectura de carga",
            description = "Recibe masa acumulada, densidad en g/cm3, temperatura y caudal. "
                    + "La primera lectura válida inicia la carga; el historial se guarda según "
                    + "integrador.readings.storage-interval-seconds.")
    @ApiResponse(responseCode = "200", description = "Lectura aceptada o descartada")
    @ApiResponse(responseCode = "400", description = "Datos incompletos o densidad fuera del rango 0 a 1 g/cm3",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe la orden",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(responseCode = "409", description = "La orden no está activada o no está en estado 2",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @PostMapping("/{orderNumber}/readings")
    public ReadingResponse registerReading(
            @Parameter(description = "Número interno de orden (orderNumber)", example = "501")
                    @PathVariable long orderNumber,
            @Valid @RequestBody ReadingRequest request)
            throws NotFoundException, InvalidStateException, InvalidDataException {
        return orderBusiness.registerReading(orderNumber, request);
    }

    @Operation(summary = "Consultar el historial muestreado de lecturas")
    @ApiResponse(responseCode = "200", description = "Historial de lecturas")
    @ApiResponse(responseCode = "404", description = "No existe la orden",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @GetMapping("/{orderNumber}/readings")
    public List<LoadDetail> readings(
            @Parameter(description = "Número interno de orden (orderNumber)", example = "501")
                    @PathVariable long orderNumber) throws NotFoundException {
        return orderBusiness.readings(orderNumber);
    }


    @Operation(summary = "Cerrar la carga y pasar de estado 2 a 3",
            description = "Bloquea nuevas lecturas e invalida la clave de activación.")
    @ApiResponse(responseCode = "200", description = "Carga cerrada")
    @ApiResponse(responseCode = "400", description = "Observación inválida")
    @ApiResponse(responseCode = "404", description = "No existe la orden")
    @ApiResponse(responseCode = "409", description = "La orden no está en estado 2")
    @PostMapping("/{orderNumber}/close")
    public Order close(@PathVariable long orderNumber, @Valid @RequestBody CloseRequest request)
            throws NotFoundException, InvalidStateException {
        return orderBusiness.close(orderNumber, request.getObservation());
    }

    @Operation(summary = "Registrar pesaje final y pasar de estado 3 a 4",
            description = "El peso bruto final no puede ser menor que la tara. Devuelve la conciliación.")
    @ApiResponse(responseCode = "200", description = "Pesaje registrado y conciliación calculada")
    @ApiResponse(responseCode = "400", description = "Peso u observación inválidos")
    @ApiResponse(responseCode = "404", description = "No existe la orden")
    @ApiResponse(responseCode = "409", description = "La orden no está en estado 3")
    @PostMapping("/{orderNumber}/final-weight")
    public ReconciliationResponse finalWeight(@PathVariable long orderNumber,
            @Valid @RequestBody FinalWeightRequest request)
            throws NotFoundException, InvalidStateException, InvalidDataException {
        return orderBusiness.finalWeight(orderNumber, request.getWeight(), request.getObservation());
    }

    @Operation(summary = "Consultar la conciliación de una orden en estado 4",
            description = "Masa cargada: última masa aceptada. Promedios: historial muestreado.")
    @ApiResponse(responseCode = "200", description = "Conciliación recalculada")
    @ApiResponse(responseCode = "404", description = "No existe la orden")
    @ApiResponse(responseCode = "409", description = "La orden no está en estado 4")
    @GetMapping("/{orderNumber}/reconciliation")
    public ReconciliationResponse reconciliation(@PathVariable long orderNumber)
            throws NotFoundException, InvalidStateException {
        return orderBusiness.reconciliation(orderNumber);
    }

    /** Activa el surtidor con la contraseña de la orden y devuelve el preset. */
    @Operation(
            summary = "Activar el surtidor",
            description =
                    "Valida la contraseña generada en la tara y devuelve el preset (kg a cargar)."
                            + " Se puede repetir mientras no haya empezado la carga, por si el"
                            + " surtidor no recibió la respuesta.")
    @ApiResponse(responseCode = "200", description = "Contraseña correcta")
    @ApiResponse(
            responseCode = "400",
            description = "La contraseña falta o no tiene 5 dígitos",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "La contraseña no corresponde a la orden",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No existe una orden con ese número",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "La orden no está en estado 2 o la carga ya comenzó",
            content = @Content(schema = @Schema(implementation = StandartResponse.class)))
    @PostMapping("/{orderNumber}/activation")
    public ActivationResponse activate(
            @Parameter(description = "Número de orden (el que mandó SAP)", example = "501")
                    @PathVariable
                    long orderNumber,
            @Valid @RequestBody ActivationRequest request)
            throws NotFoundException, InvalidStateException, InvalidPasswordException {
        return orderBusiness.activate(orderNumber, request.getPassword());
    }
}
