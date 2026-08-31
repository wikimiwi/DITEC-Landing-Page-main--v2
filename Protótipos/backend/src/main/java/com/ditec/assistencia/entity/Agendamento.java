package com.ditec.assistencia.entity;

import com.ditec.assistencia.enums.StatusAgendamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Um pedido de visita tecnica.
 *
 * DECISAO TECNICA: a landing page original permitia agendar SEM conta
 * (o cliente so era convidado a criar login depois de agendar). Para nao
 * quebrar esse fluxo, cliente_id e' opcional — quando o agendamento e' de
 * um visitante nao autenticado, nome_contato/telefone_contato guardam os
 * dados informados no formulario. Ao criar a conta logo em seguida, o
 * agendamento mais recente sem cliente_id e' vinculado automaticamente
 * (mesma logica que ja existia em localStorage, agora no backend).
 */
@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, unique = true)
    private String protocolo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "nome_contato", length = 150)
    private String nomeContato;

    @Column(name = "telefone_contato", length = 20)
    private String telefoneContato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Tecnico tecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "tipo_aparelho", nullable = false, length = 100)
    private String tipoAparelho;

    @Column(name = "descricao_problema", length = 500)
    private String descricaoProblema;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 255)
    private String endereco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusAgendamento status = StatusAgendamento.PENDENTE;

    @Column(name = "prioridade_gas", nullable = false)
    @Builder.Default
    private boolean prioridadeGas = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
        if (status == null) status = StatusAgendamento.PENDENTE;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    /** Nome de exibicao independente de o agendamento ter conta vinculada. */
    @Transient
    public String getNomeExibicao() {
        if (cliente != null && cliente.getUsuario() != null) return cliente.getUsuario().getNome();
        return nomeContato;
    }

    @Transient
    public String getTelefoneExibicao() {
        if (cliente != null && cliente.getUsuario() != null) return cliente.getUsuario().getTelefone();
        return telefoneContato;
    }
}
