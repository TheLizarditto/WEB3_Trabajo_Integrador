package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {
    List<StateTransition> findByOrderIdOrderByOccurredAtAsc(Long id);
}
