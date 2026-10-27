package ar.edu.iua.integrador.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity(name = "StateTransition")
@Table(name = "iw3_transition")
@Getter
@Setter
public class StateTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    private Integer fromState;

    @Column(nullable = false)
    private int toState;

    @Column(nullable = false)
    private String actor;

    private String observation;

    @Column(nullable = false)
    private Instant occurredAt;
}
