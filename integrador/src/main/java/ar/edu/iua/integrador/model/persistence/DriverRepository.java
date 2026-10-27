package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findOneByExternalCode(String externalCode);

    List<Driver> findAllByOrderByNameAsc();
}
