package com.ditec.assistencia.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Historico de mudancas de status de uma OS — e' o que alimenta a linha do
 * tempo visual que o cliente ve ao rastrear seu protocolo (igual ao
 * comportamento antigo dos "steps" do OS_DATA mockado).
 */
@Entity
@Table(name = "timeline_os")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineOS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @Column(name = "status_anterior", length = 30)
    private String statusAnterior;

    @Column(name = "status_novo", nullable = false, length = 30)
    private String statusNovo;

    @Column(length = 500)
    private String observacao;

    @Column(name = "alterado_por", nullable = false, length = 150)
    private String alteradoPor;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @PrePersist
    void prePersist() {
        if (dataHora == null) dataHora = LocalDateTime.now();
    }
}
