package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Table(name = "iw3_product")
@DiscriminatorValue("PRODUCT")
@Getter
@Setter
public class Product extends Master {
    private String description;

    @Override
    public Kind getKind() {
        return Kind.PRODUCT;
    }
}
