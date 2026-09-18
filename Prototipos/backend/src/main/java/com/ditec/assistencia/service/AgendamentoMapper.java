package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.agendamento.AgendamentoResponse;
import com.ditec.assistencia.entity.Agendamento;

final class AgendamentoMapper {

    private AgendamentoMapper() {
    }

    static AgendamentoResponse toResponse(Agendamento a) {
        return new AgendamentoResponse(
                a.getId(),
                a.getProtocolo(),
                a.getNomeExibicao(),
                a.getTelefoneExibicao(),
                a.getTipoAparelho(),
                a.getDescricaoProblema(),
                a.getEndereco(),
                a.getBairro(),
                a.getDataHora(),
                a.getStatus().name(),
                a.isPrioridadeGas(),
                a.getTecnico() != null ? a.getTecnico().getNome() : null,
                a.getCliente() != null,
                a.getStatus().isElegivelParaAlteracaoOuCancelamento()
        );
    }
}
