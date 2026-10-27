package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity(name = "Master")
@Table(
        name = "iw3_master",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "external_code"}))
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "kind", length = 30)
@Getter
@Setter
public abstract class Master {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String externalCode;

    @Column(nullable = false)
    private String name;

    public abstract Kind getKind();
}
