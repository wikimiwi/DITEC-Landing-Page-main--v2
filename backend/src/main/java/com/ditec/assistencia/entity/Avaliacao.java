package com.ditec.assistencia.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "avaliacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false, unique = true)
    private OrdemServico ordemServico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false)
    private Integer nota;

    @Column(length = 500)
    private String comentario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }
}
