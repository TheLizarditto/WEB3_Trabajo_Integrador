package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity(name = "LoadDetail")
@Table(
        name = "iw3_detail",
        indexes = @Index(name = "idx_detail_order", columnList = "orderId,receivedAt"))
@Getter
@Setter
public class LoadDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private double mass, density, temperature, flow;

    @Column(nullable = false)
    private Instant receivedAt;
}
