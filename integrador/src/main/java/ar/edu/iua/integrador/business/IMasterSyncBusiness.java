package ar.edu.iua.integrador.business;

import ar.edu.iua.integrador.business.exception.FoundException;
import ar.edu.iua.integrador.dto.CustomerRequest;
import ar.edu.iua.integrador.dto.DriverRequest;
import ar.edu.iua.integrador.dto.ProductRequest;
import ar.edu.iua.integrador.dto.TruckRequest;
import ar.edu.iua.integrador.model.Customer;
import ar.edu.iua.integrador.model.Driver;
import ar.edu.iua.integrador.model.Product;
import ar.edu.iua.integrador.model.Truck;

/**
 * Sincroniza los maestros que manda SAP con los de la base: si ya existen los actualiza, si no
 * los crea. Cada método devuelve la entidad guardada, lista para asignarla a la orden.
 */
public interface IMasterSyncBusiness {

    /** Sincroniza el camión y sus cisternas. Busca por código externo y, si no, por patente. */
    Truck syncTruck(TruckRequest request) throws FoundException;

    /** Sincroniza el chofer. Busca por código externo y, si no, por documento. */
    Driver syncDriver(DriverRequest request) throws FoundException;

    /** Sincroniza el cliente. Busca por código externo y, si no, por nombre. */
    Customer syncCustomer(CustomerRequest request) throws FoundException;

    /** Sincroniza el producto. Busca por código externo y, si no, por nombre. */
    Product syncProduct(ProductRequest request) throws FoundException;
}
