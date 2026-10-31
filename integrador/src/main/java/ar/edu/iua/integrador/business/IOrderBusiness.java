package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.ActivationResponse;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TareResponse;
import ar.edu.iua.integrador.model.Order;

/**
 * Operaciones sobre las órdenes de carga. Por ahora: la recepción desde SAP, la consulta, la tara
 * y la activación del surtidor.
 */
public interface IOrderBusiness {

    /**
     * Da de alta la orden que manda SAP en estado 1, sincronizando sus maestros. Lanza
     * FoundException si ya existe una orden con ese número o código externo.
     */
    Order receiveFromSap(SapOrderRequest request) throws FoundException;

    /** Devuelve la orden con ese número de orden, o NotFoundException si no existe. */
    Order loadByOrderNumber(long orderNumber) throws NotFoundException;

    /**
     * Registra la tara de una orden en estado 1, genera su contraseña de activación y la pasa a
     * estado 2. Devuelve la contraseña en claro: es la única vez, porque solo se guarda su hash.
     * Lanza InvalidStateException si la orden no está en estado 1.
     */
    TareResponse registerTare(long orderNumber, double tare)
            throws NotFoundException, InvalidStateException;

    /**
     * Valida la contraseña del surtidor y devuelve el preset. Se puede repetir mientras la carga no
     * haya empezado. Lanza InvalidStateException si la orden no está en estado 2 o la carga ya
     * empezó, e InvalidPasswordException si la contraseña no corresponde.
     */
    ActivationResponse activate(long orderNumber, String password)
            throws NotFoundException, InvalidStateException, InvalidPasswordException;
}
