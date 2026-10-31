package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.model.Order;

/** Operaciones sobre las órdenes de carga. Por ahora: la recepción desde SAP y la consulta. */
public interface IOrderBusiness {

    /**
     * Da de alta la orden que manda SAP en estado 1, sincronizando sus maestros. Lanza
     * FoundException si ya existe una orden con ese número o código externo.
     */
    Order receiveFromSap(SapOrderRequest request) throws FoundException;

    /** Devuelve la orden con ese número de orden, o NotFoundException si no existe. */
    Order loadByOrderNumber(long orderNumber) throws NotFoundException;
}
