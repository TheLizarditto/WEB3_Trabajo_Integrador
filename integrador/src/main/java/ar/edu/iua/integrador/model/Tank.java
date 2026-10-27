package ar.edu.iua.integrador.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Table(name = "iw3_compartment")
@IdClass(TankId.class)
@Getter
@Setter
public class Tank {
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "master_id")
    @JsonIgnore
    private Truck truck;

    @Id private int position;

    @Column(nullable = false)
    private double liters;
}
