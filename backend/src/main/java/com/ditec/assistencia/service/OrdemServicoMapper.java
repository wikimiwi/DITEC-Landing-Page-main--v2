package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.os.OrdemServicoResponse;
import com.ditec.assistencia.dto.os.TimelineItemResponse;
import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.entity.TimelineOS;

import java.util.List;

final class OrdemServicoMapper {

    private OrdemServicoMapper() {
    }

    static OrdemServicoResponse toResponse(OrdemServico os, List<TimelineOS> timeline) {
        List<TimelineItemResponse> timelineDto = timeline.stream()
                .map(t -> new TimelineItemResponse(t.getStatusAnterior(), t.getStatusNovo(),
                        t.getObservacao(), t.getAlteradoPor(), t.getDataHora()))
                .toList();

        String clienteNome = os.getCliente() != null && os.getCliente().getUsuario() != null
                ? os.getCliente().getUsuario().getNome()
                : os.getAgendamento().getNomeExibicao();

        return new OrdemServicoResponse(
                os.getId(),
                os.getProtocolo(),
                clienteNome,
                os.getTecnico() != null ? os.getTecnico().getNome() : null,
                os.getAgendamento().getTipoAparelho(),
                os.getAgendamento().getBairro(),
                os.getDescricaoProblema(),
                os.getStatus().name(),
                os.getValor(),
                os.getFormaPagamento(),
                os.getDesconto(),
                os.getPecasUtilizadas(),
                os.getDescricaoFinal(),
                os.getDataInicio(),
                os.getDataConclusao(),
                os.getGarantiaDias(),
                os.getNotaFiscalNumero(),
                timelineDto
        );
    }
}
