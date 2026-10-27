package ar.edu.iua.integrador.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Entity
@Table(name = "iw3_truck")
@DiscriminatorValue("TRUCK")
@Getter
@Setter
public class Truck extends Master {
    private String plate;
    private String description;

    @Override
    public Kind getKind() {
        return Kind.TRUCK;
    }

    @JsonIgnore
    @OneToMany(
            mappedBy = "truck",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<Tank> tanks = new ArrayList<>();

    public List<Double> getCompartments() {
        return tanks.stream().map(Tank::getLiters).toList();
    }

    public double getTotalLiters() {
        return tanks.stream().mapToDouble(Tank::getLiters).sum();
    }

    public void replaceCompartments(List<Double> capacities) {
        // Reuse positions so updating a managed truck never inserts a duplicate composite key.
        while (tanks.size() > capacities.size()) {
            tanks.removeLast();
        }
        for (int position = 0; position < capacities.size(); position++) {
            Tank tank;
            if (position < tanks.size()) {
                tank = tanks.get(position);
            } else {
                tank = new Tank();
                tank.setTruck(this);
                tank.setPosition(position);
                tanks.add(tank);
            }
            tank.setLiters(capacities.get(position));
        }
    }
}
