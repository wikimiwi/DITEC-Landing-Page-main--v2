package com.ditec.assistencia.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da Ordem de Servico.
 *
 * Fluxo principal:
 *   AGENDADO (aguardando avaliacao) -> EM_DIAGNOSTICO -> [AGUARDANDO_APROVACAO]
 *   -> [AGUARDANDO_PECAS] -> EM_MANUTENCAO -> EM_TESTES -> CONCLUIDO
 *   -> [AGUARDANDO_RETIRADA] -> ENTREGUE
 *
 * CANCELADO interrompe o fluxo em qualquer etapa anterior a conclusao.
 * ENTREGUE e CANCELADO sao terminais: nada mais muda de status (o
 * historico continua disponivel para consulta).
 *
 * Regras complementares (verificadas no OrdemServicoService):
 *  - CONCLUIDO so e' alcancado por POST /finalizar (exige valor e descricao
 *    da conclusao) — nunca por uma simples troca de status.
 *  - sair de EM_DIAGNOSTICO exige diagnostico registrado;
 *  - AGUARDANDO_APROVACAO exige valor de orcamento registrado.
 */
public enum StatusOS {
    AGENDADO("Aguardando avaliação", 0),
    EM_DIAGNOSTICO("Em diagnóstico", 1),
    AGUARDANDO_APROVACAO("Aguardando aprovação do orçamento", 1),
    AGUARDANDO_PECAS("Aguardando peças", 2),
    EM_MANUTENCAO("Em manutenção", 2),
    EM_TESTES("Em testes", 3),
    CONCLUIDO("Serviço concluído", 4),
    AGUARDANDO_RETIRADA("Aguardando retirada/entrega", 4),
    ENTREGUE("Entregue ao cliente", 5),
    CANCELADO("Cancelada", -1);

    /** Rotulo exibido ao usuario. */
    private final String descricao;
    /** Etapa macro mostrada no indicador visual do rastreamento (0..5; -1 = cancelada). */
    private final int etapa;

    StatusOS(String descricao, int etapa) {
        this.descricao = descricao;
        this.etapa = etapa;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getEtapa() {
        return etapa;
    }

    private static final Map<StatusOS, Set<StatusOS>> TRANSICOES = Map.of(
            AGENDADO, EnumSet.of(EM_DIAGNOSTICO, CANCELADO),
            EM_DIAGNOSTICO, EnumSet.of(AGUARDANDO_APROVACAO, AGUARDANDO_PECAS, EM_MANUTENCAO, CANCELADO),
            AGUARDANDO_APROVACAO, EnumSet.of(AGUARDANDO_PECAS, EM_MANUTENCAO, CANCELADO),
            AGUARDANDO_PECAS, EnumSet.of(EM_MANUTENCAO, CANCELADO),
            EM_MANUTENCAO, EnumSet.of(AGUARDANDO_PECAS, EM_TESTES, CANCELADO),
            EM_TESTES, EnumSet.of(EM_MANUTENCAO, CONCLUIDO),
            CONCLUIDO, EnumSet.of(AGUARDANDO_RETIRADA, ENTREGUE),
            AGUARDANDO_RETIRADA, EnumSet.of(ENTREGUE),
            ENTREGUE, EnumSet.noneOf(StatusOS.class),
            CANCELADO, EnumSet.noneOf(StatusOS.class)
    );

    public boolean podeTransicionarPara(StatusOS novo) {
        return novo != null && TRANSICOES.get(this).contains(novo);
    }

    /** Proximos status validos a partir deste (inclui CONCLUIDO, que so' e' atingido via /finalizar). */
    public Set<StatusOS> proximos() {
        return TRANSICOES.get(this).isEmpty() ? EnumSet.noneOf(StatusOS.class) : EnumSet.copyOf(TRANSICOES.get(this));
    }

    public boolean isTerminal() {
        return this == ENTREGUE || this == CANCELADO;
    }

    /** Servico ja foi concluido (com ou sem entrega). */
    public boolean isServicoConcluido() {
        return this == CONCLUIDO || this == AGUARDANDO_RETIRADA || this == ENTREGUE;
    }

    /** Em andamento = tecnico ja iniciou e o servico ainda nao foi concluido. */
    public boolean isEmAndamento() {
        return etapa >= 1 && etapa <= 3;
    }

    /** Status equivalente do Agendamento (que tem um ciclo mais simples). */
    public StatusAgendamento paraStatusAgendamento() {
        if (this == AGENDADO) return StatusAgendamento.AGENDADO;
        if (this == CANCELADO) return StatusAgendamento.CANCELADO;
        if (isServicoConcluido()) return StatusAgendamento.CONCLUIDO;
        return StatusAgendamento.EM_ATENDIMENTO;
    }

    /** Status considerados "em aberto" (nao concluidos nem cancelados) — dashboard/admin. */
    public static Set<StatusOS> abertos() {
        return EnumSet.of(AGENDADO, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, AGUARDANDO_PECAS, EM_MANUTENCAO, EM_TESTES);
    }
}
