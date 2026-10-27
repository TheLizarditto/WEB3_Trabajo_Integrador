package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Table(name = "iw3_customer")
@DiscriminatorValue("CUSTOMER")
@Getter
@Setter
public class Customer extends Master {
    private String contact;

    @Override
    public Kind getKind() {
        return Kind.CUSTOMER;
    }
}
