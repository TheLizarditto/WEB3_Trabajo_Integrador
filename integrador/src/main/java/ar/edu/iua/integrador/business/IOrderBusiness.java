package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.ReconciliationResponse;
import ar.edu.iua.integrador.dto.ActivationResponse;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.ReadingRequest;
import ar.edu.iua.integrador.dto.ReadingResponse;
import ar.edu.iua.integrador.model.LoadDetail;
import ar.edu.iua.integrador.business.exception.InvalidDataException;
import java.util.List;
import ar.edu.iua.integrador.dto.TareResponse;
import ar.edu.iua.integrador.model.Order;

/**
 * Operaciones del circuito de órdenes, desde la recepción hasta la conciliación.
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

    /** Procesa una lectura de una orden activada en estado 2. */
    ReadingResponse registerReading(long orderNumber, ReadingRequest reading)
            throws NotFoundException, InvalidStateException, InvalidDataException;

    /** Cierra la carga en estado 2, pasa a estado 3 y bloquea nuevas lecturas. */
    Order close(long orderNumber, String observation)
            throws NotFoundException, InvalidStateException;

    /** Registra el peso bruto final en estado 3 y devuelve la conciliación en estado 4. */
    ReconciliationResponse finalWeight(long orderNumber, double weight, String observation)
            throws NotFoundException, InvalidStateException, InvalidDataException;

    /** Recalcula la conciliación; solo está disponible en estado 4. */
    ReconciliationResponse reconciliation(long orderNumber)
            throws NotFoundException, InvalidStateException;

    /** Devuelve el historial muestreado de lecturas de la orden. */
    List<LoadDetail> readings(long orderNumber) throws NotFoundException;
}
