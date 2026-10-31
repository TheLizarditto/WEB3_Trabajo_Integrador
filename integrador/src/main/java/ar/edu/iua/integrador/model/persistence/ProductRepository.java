package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findOneByExternalCode(String externalCode);

    // Busca por nombre cuando no se lo encuentra por código externo. Devuelve lista porque
    // el nombre no es único en la base; ordenada por id para usar siempre el más viejo.
    List<Product> findAllByNameOrderByIdAsc(String name);

    List<Product> findAllByOrderByNameAsc();
}
