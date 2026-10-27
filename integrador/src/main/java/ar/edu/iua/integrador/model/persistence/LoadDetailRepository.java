package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface LoadDetailRepository extends JpaRepository<LoadDetail, Long> {
    List<LoadDetail> findByOrderIdOrderByReceivedAtAsc(Long orderId);

    @Query(
            "select avg(d.temperature),avg(d.density),avg(d.flow) from LoadDetail d where"
                    + " d.orderId=:id")
    List<Object[]> averages(@Param("id") Long id);
}
