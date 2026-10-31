package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.StateTransition;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;
import ar.edu.iua.integrador.util.TextUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Implementa las operaciones de órdenes. */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderBusiness implements IOrderBusiness {

    private static final int STATE_PENDING_INITIAL_WEIGHING = 1;
    private static final String ACTOR_SAP = "SAP";

    private final OrderRepository orderRepository;
    private final StateTransitionRepository transitionRepository;
    private final IMasterSyncBusiness masterSyncBusiness;

    // Todo en una transacción: si algo falla, se deshacen también los maestros sincronizados.
    // rollbackFor hace falta porque nuestras excepciones son chequeadas, y por defecto Spring
    // solo deshace la transacción con RuntimeException.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order receiveFromSap(SapOrderRequest request) throws FoundException {
        String externalCode = TextUtil.blankToNull(request.getExternalCode());

        // Se valida antes de sincronizar, así una orden repetida no toca los maestros.
        if (orderRepository.existsByOrderNumber(request.getOrderNumber())) {
            throw FoundException.builder()
                    .message("Ya existe una orden con número " + request.getOrderNumber())
                    .build();
        }
        if (externalCode != null && orderRepository.existsByExternalCode(externalCode)) {
            throw FoundException.builder()
                    .message("Ya existe una orden con código externo '" + externalCode + "'")
                    .build();
        }

        Order order = new Order();
        order.setOrderNumber(request.getOrderNumber());
        order.setExternalCode(externalCode);
        order.setPreset(request.getPreset());
        order.setScheduledAt(request.getScheduledAt());
        order.setReceivedAt(Instant.now());
        order.setState(STATE_PENDING_INITIAL_WEIGHING);

        // Dos pedidos al mismo tiempo pasan los dos los existsBy de arriba (y las búsquedas de la
        // sincronización); al segundo lo frena un índice único de la base: el de la orden, o el
        // de un maestro nuevo con el mismo código. Para SAP es lo mismo: los datos ya existen, 409.
        try {
            order.setTruck(masterSyncBusiness.syncTruck(request.getTruck()));
            order.setDriver(masterSyncBusiness.syncDriver(request.getDriver()));
            order.setCustomer(masterSyncBusiness.syncCustomer(request.getCustomer()));
            order.setProduct(masterSyncBusiness.syncProduct(request.getProduct()));
            // saveAndFlush manda los INSERT/UPDATE pendientes ya, así el error salta acá adentro
            // y no recién al terminar la transacción, fuera de este try.
            order = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            throw FoundException.builder()
                    .message(
                            "La orden "
                                    + request.getOrderNumber()
                                    + " choca con datos que ya existen (número o código externo"
                                    + " repetido en la orden o en un maestro)")
                    .ex(ex)
                    .build();
        }

        StateTransition transition = new StateTransition();
        transition.setOrderId(order.getId());
        // null y no 0: antes de llegar de SAP la orden no tenía ningún estado.
        transition.setFromState(null);
        transition.setToState(STATE_PENDING_INITIAL_WEIGHING);
        transition.setActor(ACTOR_SAP);
        transition.setOccurredAt(order.getReceivedAt());
        transitionRepository.save(transition);

        log.info("Orden {} recibida de SAP con id {}", order.getOrderNumber(), order.getId());
        return order;
    }

    @Override
    public Order loadByOrderNumber(long orderNumber) throws NotFoundException {
        return orderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(
                        () ->
                                NotFoundException.builder()
                                        .message("No existe la orden número " + orderNumber)
                                        .build());
    }
}
