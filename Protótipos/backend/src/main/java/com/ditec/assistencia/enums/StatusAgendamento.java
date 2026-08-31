package com.ditec.assistencia.enums;

/**
 * Ciclo de vida de um Agendamento.
 *
 * PENDENTE       -> criado pelo cliente/site, aguardando confirmacao da DITEC
 * AGENDADO       -> confirmado, tecnico e horario reservados
 * EM_ATENDIMENTO -> tecnico em atendimento (espelha o status da OS)
 * CONCLUIDO      -> atendimento finalizado (espelha o status da OS)
 * CANCELADO      -> cancelado pelo cliente ou pela DITEC
 *
 * Regra de negocio (DRS secao 23): agendamento em EM_ATENDIMENTO ou
 * CONCLUIDO nao pode mais ser alterado nem cancelado pelo cliente.
 */
public enum StatusAgendamento {
    PENDENTE,
    AGENDADO,
    EM_ATENDIMENTO,
    CONCLUIDO,
    CANCELADO;

    public boolean isElegivelParaAlteracaoOuCancelamento() {
        return this == PENDENTE || this == AGENDADO;
    }
}
