package com.ditec.assistencia.entity;

import com.ditec.assistencia.enums.TipoEventoOS;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TipoEventoOS tipo = TipoEventoOS.STATUS;

    @Column(name = "status_anterior", length = 30)
    private String statusAnterior;

    @Column(name = "status_novo", nullable = false, length = 30)
    private String statusNovo;

    @Column(length = 500)
    private String observacao;

    /** Nome de quem fez a alteracao (mantido mesmo se o usuario for removido). */
    @Column(name = "alterado_por", nullable = false, length = 150)
    private String alteradoPor;

    /** Usuario responsavel (null = sistema). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** false = anotacao interna, nao aparece no rastreamento do cliente. */
    @Column(name = "visivel_cliente", nullable = false)
    @Builder.Default
    private boolean visivelCliente = true;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @PrePersist
    void prePersist() {
        if (dataHora == null) dataHora = LocalDateTime.now();
        if (tipo == null) tipo = TipoEventoOS.STATUS;
    }
}
