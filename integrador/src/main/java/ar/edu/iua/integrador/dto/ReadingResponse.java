package ar.edu.iua.integrador.dto;

import ar.edu.iua.integrador.model.Order;

/** Resultado del procesamiento y estado actual de la orden. */
public record ReadingResponse(boolean accepted, boolean stored, String reason, Order order) {}
