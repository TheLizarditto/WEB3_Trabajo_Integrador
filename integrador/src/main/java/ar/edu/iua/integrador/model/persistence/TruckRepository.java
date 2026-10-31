package ar.edu.iua.integrador.model.persistence;

import ar.edu.iua.integrador.model.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface TruckRepository extends JpaRepository<Truck, Long> {
    Optional<Truck> findOneByExternalCode(String externalCode);

    // Busca por patente cuando no se lo encuentra por código externo. Devuelve lista porque
    // la patente no es única en la base; ordenada por id para usar siempre el más viejo.
    List<Truck> findAllByPlateOrderByIdAsc(String plate);

    List<Truck> findAllByOrderByNameAsc();
}
