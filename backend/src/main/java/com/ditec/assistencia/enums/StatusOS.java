package com.ditec.assistencia.enums;

/**
 * Progressao da Ordem de Servico (DRS secao 23).
 * Regra: AGENDADO -> EM_ATENDIMENTO -> CONCLUIDO, sem regressao.
 * CANCELADO e' um estado terminal alternativo (interrompe o fluxo normal).
 */
public enum StatusOS {
    AGENDADO(0),
    EM_ATENDIMENTO(1),
    CONCLUIDO(2),
    CANCELADO(3);

    private final int ordemProgressao;

    StatusOS(int ordemProgressao) {
        this.ordemProgressao = ordemProgressao;
    }

    public boolean podeTransicionarPara(StatusOS novo) {
        if (novo == this) return false;
        if (this == CONCLUIDO || this == CANCELADO) return false;
        if (novo == CANCELADO) return true;
        return novo.ordemProgressao > this.ordemProgressao;
    }
}
