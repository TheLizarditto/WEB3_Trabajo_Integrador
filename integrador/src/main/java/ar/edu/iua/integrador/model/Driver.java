package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Table(name = "iw3_driver")
@DiscriminatorValue("DRIVER")
@Getter
@Setter
public class Driver extends Master {
    private String lastName;
    private String document;

    @Override
    public Kind getKind() {
        return Kind.DRIVER;
    }
}
