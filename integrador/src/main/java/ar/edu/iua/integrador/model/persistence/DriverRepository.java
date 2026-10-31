package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findOneByExternalCode(String externalCode);

    // Busca por documento cuando no se lo encuentra por código externo. Devuelve lista porque
    // el documento no es único en la base; ordenada por id para usar siempre el más viejo.
    List<Driver> findAllByDocumentOrderByIdAsc(String document);

    List<Driver> findAllByOrderByNameAsc();
}
