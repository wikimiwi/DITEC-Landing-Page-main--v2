package com.ditec.assistencia.enums;

/** Tipo de cada registro do historico (timeline_os) de uma Ordem de Servico. */
public enum TipoEventoOS {
    /** Abertura da OS (criada junto com o agendamento). */
    ABERTURA,
    /** Mudanca de status. */
    STATUS,
    /** Diagnostico registrado/atualizado pelo tecnico. */
    DIAGNOSTICO,
    /** Orcamento informado, aprovado ou recusado. */
    ORCAMENTO,
    /** Observacao livre (tecnica ou de atendimento). */
    OBSERVACAO,
    /** Tecnico atribuido/alterado. */
    ATRIBUICAO,
    /** Previsao de conclusao registrada/alterada. */
    PREVISAO
}
