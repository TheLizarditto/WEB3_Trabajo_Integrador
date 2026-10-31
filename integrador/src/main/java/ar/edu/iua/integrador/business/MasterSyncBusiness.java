package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.dto.CustomerRequest;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.ProductRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.Customer;
import ar.edu.iua.integrador.model.Driver;
import ar.edu.iua.integrador.model.Master;
import ar.edu.iua.integrador.model.Product;
import ar.edu.iua.integrador.model.Truck;
import ar.edu.iua.integrador.model.persistence.CustomerRepository;
import ar.edu.iua.integrador.model.persistence.DriverRepository;
import ar.edu.iua.integrador.model.persistence.ProductRepository;
import ar.edu.iua.integrador.model.persistence.TruckRepository;
import ar.edu.iua.integrador.util.TextUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Implementa la sincronización de maestros. Los cuatro métodos siguen la misma regla:
 * 1. Existe por código externo → se actualiza ese.
 * 2. Vino con código, no existe por código pero sí por dato natural sin código → se le asigna.
 * 3. Igual que el 2, pero el encontrado ya tiene otro código → 409, sin modificar nada.
 * 4. Vino sin código y existe por dato natural → se actualiza ese (conserva su código).
 * 5. No existe por ningún lado → se crea uno nuevo.
 * En los casos 1, 2 y 4 se pisan los datos con lo que manda SAP, que es la fuente de verdad.
 * Excepción: los campos opcionales (descripción, contacto, documento) solo se pisan si vienen con
 * valor. Si vienen null o vacíos se conserva lo que había, igual que el código en el caso 4.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MasterSyncBusiness implements IMasterSyncBusiness {

    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Override
    public Truck syncTruck(TruckRequest request) throws FoundException {
        String code = TextUtil.blankToNull(request.getExternalCode());
        String plate = request.getPlate();

        Optional<Truck> byCode =
                code == null ? Optional.empty() : truckRepository.findOneByExternalCode(code);
        Truck truck;
        if (byCode.isPresent()) {
            // Caso 1: existe por código → se actualiza ese.
            truck = byCode.get();
        } else {
            List<Truck> byPlate = truckRepository.findAllByPlateOrderByIdAsc(plate);
            if (byPlate.size() > 1) {
                log.warn("Hay {} camiones con patente '{}': se usa el más viejo", byPlate.size(), plate);
            }
            if (byPlate.isEmpty()) {
                // Caso 5: no existe en ningún lado → se crea uno nuevo.
                truck = new Truck();
                truck.setExternalCode(code);
            } else if (code == null) {
                // Caso 4: vino sin código y existe por patente → se actualiza ese.
                truck = byPlate.getFirst();
            } else {
                truck = byPlate.getFirst();
                // Caso 3: existe por patente pero con otro código → 409.
                rejectIfHasOtherCode(truck, code, "camión", "patente", plate);
                // Caso 2: existe por patente sin código → le asigno el código.
                truck.setExternalCode(code);
            }
        }

        // SAP no manda un nombre para el camión, pero Master.name es obligatorio:
        // usamos la patente, que es lo que lo identifica y siempre viene.
        truck.setName(plate);
        truck.setPlate(plate);
        String description = TextUtil.blankToNull(request.getDescription());
        if (description != null) {
            truck.setDescription(description);
        }
        truck.replaceCompartments(request.getTanks());
        return truckRepository.save(truck);
    }

    @Override
    public Driver syncDriver(DriverRequest request) throws FoundException {
        String code = TextUtil.blankToNull(request.getExternalCode());
        String document = request.getDocument();

        Optional<Driver> byCode =
                code == null ? Optional.empty() : driverRepository.findOneByExternalCode(code);
        Driver driver;
        if (byCode.isPresent()) {
            // Caso 1: existe por código → se actualiza ese.
            driver = byCode.get();
        } else {
            // A diferencia de los otros maestros, el documento es opcional: sin él no se puede buscar.
            List<Driver> byDocument =
                    document == null
                            ? List.of()
                            : driverRepository.findAllByDocumentOrderByIdAsc(document);
            if (byDocument.size() > 1) {
                log.warn(
                        "Hay {} choferes con documento '{}': se usa el más viejo",
                        byDocument.size(),
                        document);
            }
            if (byDocument.isEmpty()) {
                // Caso 5: no existe en ningún lado → se crea uno nuevo.
                if (code == null && document == null) {
                    // No hay forma de saber si ya existe: lo dejamos avisado por si se duplica.
                    log.warn("Llegó un chofer sin código externo ni documento: se crea uno nuevo");
                }
                driver = new Driver();
                driver.setExternalCode(code);
            } else if (code == null) {
                // Caso 4: vino sin código y existe por documento → se actualiza ese.
                driver = byDocument.getFirst();
            } else {
                driver = byDocument.getFirst();
                // Caso 3: existe por documento pero con otro código → 409.
                rejectIfHasOtherCode(driver, code, "chofer", "documento", document);
                // Caso 2: existe por documento sin código → le asigno el código.
                driver.setExternalCode(code);
            }
        }

        driver.setName(request.getName());
        driver.setLastName(request.getLastName());
        if (document != null) {
            driver.setDocument(document);
        }
        return driverRepository.save(driver);
    }

    @Override
    public Customer syncCustomer(CustomerRequest request) throws FoundException {
        String code = TextUtil.blankToNull(request.getExternalCode());
        String name = request.getName();

        Optional<Customer> byCode =
                code == null ? Optional.empty() : customerRepository.findOneByExternalCode(code);
        Customer customer;
        if (byCode.isPresent()) {
            // Caso 1: existe por código → se actualiza ese.
            customer = byCode.get();
        } else {
            List<Customer> byName = customerRepository.findAllByNameOrderByIdAsc(name);
            if (byName.size() > 1) {
                log.warn("Hay {} clientes con nombre '{}': se usa el más viejo", byName.size(), name);
            }
            if (byName.isEmpty()) {
                // Caso 5: no existe en ningún lado → se crea uno nuevo.
                customer = new Customer();
                customer.setExternalCode(code);
            } else if (code == null) {
                // Caso 4: vino sin código y existe por nombre → se actualiza ese.
                customer = byName.getFirst();
            } else {
                customer = byName.getFirst();
                // Caso 3: existe por nombre pero con otro código → 409.
                rejectIfHasOtherCode(customer, code, "cliente", "nombre", name);
                // Caso 2: existe por nombre sin código → le asigno el código.
                customer.setExternalCode(code);
            }
        }

        customer.setName(name);
        String contact = TextUtil.blankToNull(request.getContact());
        if (contact != null) {
            customer.setContact(contact);
        }
        return customerRepository.save(customer);
    }

    @Override
    public Product syncProduct(ProductRequest request) throws FoundException {
        String code = TextUtil.blankToNull(request.getExternalCode());
        String name = request.getName();

        Optional<Product> byCode =
                code == null ? Optional.empty() : productRepository.findOneByExternalCode(code);
        Product product;
        if (byCode.isPresent()) {
            // Caso 1: existe por código → se actualiza ese.
            product = byCode.get();
        } else {
            List<Product> byName = productRepository.findAllByNameOrderByIdAsc(name);
            if (byName.size() > 1) {
                log.warn("Hay {} productos con nombre '{}': se usa el más viejo", byName.size(), name);
            }
            if (byName.isEmpty()) {
                // Caso 5: no existe en ningún lado → se crea uno nuevo.
                product = new Product();
                product.setExternalCode(code);
            } else if (code == null) {
                // Caso 4: vino sin código y existe por nombre → se actualiza ese.
                product = byName.getFirst();
            } else {
                product = byName.getFirst();
                // Caso 3: existe por nombre pero con otro código → 409.
                rejectIfHasOtherCode(product, code, "producto", "nombre", name);
                // Caso 2: existe por nombre sin código → le asigno el código.
                product.setExternalCode(code);
            }
        }

        product.setName(name);
        String description = TextUtil.blankToNull(request.getDescription());
        if (description != null) {
            product.setDescription(description);
        }
        return productRepository.save(product);
    }

    // Se llama antes de copiar los campos, así si hay conflicto el registro queda como estaba.
    // Recibe Master porque los cuatro maestros heredan de ahí y tienen getExternalCode().
    private void rejectIfHasOtherCode(
            Master found, String code, String kind, String naturalKeyName, String naturalKey)
            throws FoundException {
        if (found.getExternalCode() != null) {
            throw FoundException.builder()
                    .message(
                            String.format(
                                    "El %s con %s '%s' ya tiene el código externo '%s' y SAP mandó '%s'",
                                    kind, naturalKeyName, naturalKey, found.getExternalCode(), code))
                    .build();
        }
    }
}
