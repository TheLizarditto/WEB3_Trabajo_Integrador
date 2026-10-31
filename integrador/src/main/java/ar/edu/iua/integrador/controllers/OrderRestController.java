package ar.edu.iua.integrador.controllers;

import ar.edu.iua.integrador.business.IOrderBusiness;
import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.ActivationRequest;
import ar.edu.iua.integrador.dto.ActivationResponse;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TareRequest;
import ar.edu.iua.integrador.dto.TareResponse;
import ar.edu.iua.integrador.model.Order;
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
