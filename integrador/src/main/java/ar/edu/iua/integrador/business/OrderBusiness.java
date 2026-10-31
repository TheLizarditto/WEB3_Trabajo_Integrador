package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.business.exception.InvalidPasswordException;
import ar.edu.iua.integrador.business.exception.InvalidStateException;
import ar.edu.iua.integrador.business.exception.NotFoundException;
import ar.edu.iua.integrador.dto.ActivationResponse;
import ar.edu.iua.integrador.dto.SapOrderRequest;
import ar.edu.iua.integrador.dto.TareResponse;
import ar.edu.iua.integrador.model.Order;
import ar.edu.iua.integrador.model.StateTransition;
import ar.edu.iua.integrador.model.persistence.OrderRepository;
import ar.edu.iua.integrador.model.persistence.StateTransitionRepository;
import ar.edu.iua.integrador.util.TextUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

/** Implementa las operaciones de órdenes. */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderBusiness implements IOrderBusiness {

    private static final int STATE_PENDING_INITIAL_WEIGHING = 1;
    private static final int STATE_WITH_INITIAL_WEIGHING = 2;
    private static final String ACTOR_SAP = "SAP";
    private static final String ACTOR_TMS = "TMS";

    // SecureRandom y no Random: la contraseña es un secreto y Random es predecible.
    private static final SecureRandom RANDOM = new SecureRandom();
    // BCrypt agrega una sal distinta a cada hash: con solo 100.000 contraseñas posibles, un hash sin
    // sal (por ejemplo SHA-256) se rompe probándolas todas.
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TareResponse registerTare(long orderNumber, double tare)
            throws NotFoundException, InvalidStateException {
        Order order = lockByOrderNumber(orderNumber);
        if (order.getState() != STATE_PENDING_INITIAL_WEIGHING) {
            throw InvalidStateException.builder()
                    .message(
                            "La orden "
                                    + orderNumber
                                    + " está en estado "
                                    + order.getState()
                                    + ": la tara solo se registra en estado "
                                    + STATE_PENDING_INITIAL_WEIGHING)
                    .build();
        }

        // %05d completa con ceros a la izquierda: 4217 queda "04217", siempre 5 dígitos.
        String password = String.format("%05d", RANDOM.nextInt(100_000));

        order.setTare(tare);
        order.setInitialWeighedAt(Instant.now());
        order.setActivationHash(PASSWORD_ENCODER.encode(password));
        order.setState(STATE_WITH_INITIAL_WEIGHING);

        StateTransition transition = new StateTransition();
        transition.setOrderId(order.getId());
        transition.setFromState(STATE_PENDING_INITIAL_WEIGHING);
        transition.setToState(STATE_WITH_INITIAL_WEIGHING);
        transition.setActor(ACTOR_TMS);
        transition.setObservation("Tara: " + tare + " kg");
        transition.setOccurredAt(order.getInitialWeighedAt());
        transitionRepository.save(transition);

        // La contraseña no se loguea nunca: los logs no son un lugar seguro.
        log.info("Tara registrada para la orden {}: {} kg", orderNumber, tare);
        return new TareResponse(orderNumber, tare, password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivationResponse activate(long orderNumber, String password)
            throws NotFoundException, InvalidStateException, InvalidPasswordException {
        Order order = lockByOrderNumber(orderNumber);
        if (order.getState() != STATE_WITH_INITIAL_WEIGHING) {
            throw InvalidStateException.builder()
                    .message(
                            "La orden "
                                    + orderNumber
                                    + " está en estado "
                                    + order.getState()
                                    + ": el surtidor solo se activa en estado "
                                    + STATE_WITH_INITIAL_WEIGHING)
                    .build();
        }
        // startedAt se completa con la primera lectura válida. Hasta entonces se puede activar de
        // nuevo (si el surtidor perdió la respuesta, sin el preset no puede cargar); después no,
        // porque la carga ya está en marcha.
        if (order.getStartedAt() != null) {
            throw InvalidStateException.builder()
                    .message("La carga de la orden " + orderNumber + " ya comenzó")
                    .build();
        }
        if (!PASSWORD_ENCODER.matches(password, order.getActivationHash())) {
            log.warn("Contraseña de activación incorrecta para la orden {}", orderNumber);
            throw InvalidPasswordException.builder()
                    .message("Contraseña de activación incorrecta para la orden " + orderNumber)
                    .build();
        }

        // Si se activa de nuevo se conserva la fecha de la primera activación.
        if (order.getActivatedAt() == null) {
            order.setActivatedAt(Instant.now());
        }
        log.info("Surtidor activado para la orden {}", orderNumber);
        return new ActivationResponse(orderNumber, order.getPreset());
    }

    // Busca la orden bloqueándola: solo sirve dentro de un método @Transactional.
    private Order lockByOrderNumber(long orderNumber) throws NotFoundException {
        return orderRepository
                .lockByOrderNumber(orderNumber)
                .orElseThrow(
                        () ->
                                NotFoundException.builder()
                                        .message("No existe la orden número " + orderNumber)
                                        .build());
    }
}
