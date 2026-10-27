package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findOneByExternalCode(String externalCode);

    List<Customer> findAllByOrderByNameAsc();
}
