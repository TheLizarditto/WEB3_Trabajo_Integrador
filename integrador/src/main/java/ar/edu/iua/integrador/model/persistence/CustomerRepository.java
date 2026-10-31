package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findOneByExternalCode(String externalCode);

    // Busca por nombre cuando no se lo encuentra por código externo. Devuelve lista porque
    // el nombre no es único en la base; ordenada por id para usar siempre el más viejo.
    List<Customer> findAllByNameOrderByIdAsc(String name);

    List<Customer> findAllByOrderByNameAsc();
}
