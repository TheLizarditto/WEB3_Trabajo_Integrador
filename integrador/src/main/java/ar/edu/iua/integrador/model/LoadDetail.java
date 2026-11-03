package ar.edu.iua.integrador.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private double mass;
    @Schema(description = "Densidad registrada, en g/cm3")
    private double density;
    private double temperature, flow;

    @Column(nullable = false)
    private Instant receivedAt;
}
