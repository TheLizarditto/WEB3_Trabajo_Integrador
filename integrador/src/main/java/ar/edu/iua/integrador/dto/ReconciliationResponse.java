package ar.edu.iua.integrador.dto;

/** Masa de cabecera y promedios del historial muestreado, en estado 4. */
public record ReconciliationResponse(
        Long id,
        String externalCode,
        Long orderNumber,
        double tare,
        double finalWeight,
        double loadedProduct,
        double netWeight,
        double difference,
        Double averageTemperature,
        Double averageDensity,
        Double averageFlow,
        String unit) {}
