package ar.edu.iua.integrador.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

/** Fábrica de respuestas de error: arma la StandartResponse con su código HTTP. */
public final class StandartResponseBusiness {

    private StandartResponseBusiness() {}

    /** Devuelve una respuesta lista para el controller, con el status y un mensaje para el cliente. */
    public static ResponseEntity<StandartResponse> build(HttpStatus status, String message) {
        var body =
                new StandartResponse(
                        status.value(), status.getReasonPhrase(), message, Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
