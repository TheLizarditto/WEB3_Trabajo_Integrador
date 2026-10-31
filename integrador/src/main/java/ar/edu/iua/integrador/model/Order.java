package ar.edu.iua.integrador.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity(name = "Order")
@Table(name = "iw3_order")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderNumber;

    @Column(unique = true, length = 100)
    private String externalCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @ManyToOne(optional = false)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private int state;

    @Column(nullable = false)
    private double preset;

    // Double y no double: null significa que la balanza todavía no mandó ese pesaje.
    private Double tare, finalWeight;

    private double lastMass, lastDensity, lastTemperature, lastFlow;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private Instant scheduledAt;

    private Instant initialWeighedAt,
            activatedAt,
            startedAt,
            finishedAt,
            finalWeighedAt,
            lastReceivedAt,
            lastStoredAt;
    @JsonIgnore private String activationHash;
    @Version private long version;

    public Double getEtaSeconds() {
        return state == 2 && lastFlow > 0
                ? Math.max(0, (preset - lastMass) / lastFlow * 3600)
                : null;
    }

    public Long getElapsedSeconds() {
        return state == 2 && startedAt != null
                ? Math.max(0, java.time.Duration.between(startedAt, Instant.now()).getSeconds())
                : null;
    }
}
