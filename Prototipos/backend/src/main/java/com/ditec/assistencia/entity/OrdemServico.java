package com.ditec.assistencia.entity;

import com.ditec.assistencia.enums.StatusOS;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ordem de Servico. Criada automaticamente junto com o Agendamento (o
 * protocolo e' o mesmo dos dois), pois o rastreamento por protocolo ja
 * existia na landing page antes mesmo da confirmacao do atendimento.
 */
@Entity
@Table(name = "ordem_servico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, unique = true)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Tecnico tecnico;

    @Column(nullable = false, unique = true, length = 20)
    private String protocolo;

    @Column(name = "descricao_problema", length = 500)
    private String descricaoProblema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusOS status = StatusOS.AGENDADO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "forma_pagamento", length = 30)
    private String formaPagamento;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "pecas_utilizadas", columnDefinition = "TEXT")
    private String pecasUtilizadas;

    @Column(name = "descricao_final", columnDefinition = "TEXT")
    private String descricaoFinal;

    @Column(name = "data_inicio")
    private LocalDateTime dataInicio;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    @Column(name = "garantia_dias")
    @Builder.Default
    private Integer garantiaDias = 90;

    @Column(name = "nota_fiscal_numero", length = 50)
    private String notaFiscalNumero;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
        if (status == null) status = StatusOS.AGENDADO;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
