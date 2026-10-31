package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;
import ar.edu.iua.integrador.model.Order;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.externalCode=:code")
    Optional<Order> lockByCode(@Param("code") String code);

    Optional<Order> findByExternalCode(String code);

    boolean existsByOrderNumber(Long number);

    // Las URLs de órdenes usan el número de orden (lo que conocen SAP y el TMS), no el id.
    Optional<Order> findByOrderNumber(Long number);

    // Para los cambios sobre una orden (tara, activación): bloquea la fila hasta el fin de la
    // transacción, así dos pedidos simultáneos sobre la misma orden se atienden de a uno.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber=:number")
    Optional<Order> lockByOrderNumber(@Param("number") Long number);

    boolean existsByExternalCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id")
    Optional<Order> lockById(@Param("id") Long id);

    List<Order> findAllByOrderByReceivedAtDesc();
}
