package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findOneByExternalCode(String externalCode);

    List<Product> findAllByOrderByNameAsc();
}
