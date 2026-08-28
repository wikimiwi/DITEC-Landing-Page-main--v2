package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.os.OrdemServicoRastreioResponse;
import com.ditec.assistencia.dto.os.OrdemServicoResponse;
import com.ditec.assistencia.dto.os.TimelineItemResponse;
import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.entity.TimelineOS;

import java.util.List;

final class OrdemServicoMapper {

    private OrdemServicoMapper() {
    }

    static OrdemServicoResponse toResponse(OrdemServico os, List<TimelineOS> timeline) {
        List<TimelineItemResponse> timelineDto = mapTimeline(timeline);

        return new OrdemServicoResponse(
                os.getId(),
                os.getProtocolo(),
                nomeCliente(os),
                os.getTecnico() != null ? os.getTecnico().getNome() : null,
                os.getAgendamento().getTipoAparelho(),
                os.getAgendamento().getBairro(),
                os.getAgendamento().getEndereco(),
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

    /**
     * Versao PUBLICA (sem login) do rastreamento por protocolo — ver
     * OrdemServicoRastreioResponse para a justificativa de seguranca. Nunca
     * inclui endereco completo, valor, forma de pagamento, desconto, pecas
     * utilizadas ou nota fiscal, e o nome do cliente vem abreviado.
     */
    static OrdemServicoRastreioResponse toRastreioResponse(OrdemServico os, List<TimelineOS> timeline) {
        return new OrdemServicoRastreioResponse(
                os.getProtocolo(),
                abreviarNome(nomeCliente(os)),
                os.getTecnico() != null ? os.getTecnico().getNome() : null,
                os.getAgendamento().getTipoAparelho(),
                os.getAgendamento().getBairro(),
                os.getStatus().name(),
                os.getGarantiaDias(),
                mapTimeline(timeline)
        );
    }

    private static List<TimelineItemResponse> mapTimeline(List<TimelineOS> timeline) {
        return timeline.stream()
                .map(t -> new TimelineItemResponse(t.getStatusAnterior(), t.getStatusNovo(),
                        t.getObservacao(), t.getAlteradoPor(), t.getDataHora()))
                .toList();
    }

    private static String nomeCliente(OrdemServico os) {
        return os.getCliente() != null && os.getCliente().getUsuario() != null
                ? os.getCliente().getUsuario().getNome()
                : os.getAgendamento().getNomeExibicao();
    }

    /** "Maria Fernanda Souza" -> "Maria Fernanda S." — primeiro(s) nome(s) + inicial do ultimo sobrenome. */
    private static String abreviarNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "Cliente";
        String[] partes = nomeCompleto.trim().split("\\s+");
        if (partes.length == 1) return partes[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length - 1; i++) {
            if (i > 0) sb.append(' ');
            sb.append(partes[i]);
        }
        sb.append(' ').append(Character.toUpperCase(partes[partes.length - 1].charAt(0))).append('.');
        return sb.toString();
    }
}
