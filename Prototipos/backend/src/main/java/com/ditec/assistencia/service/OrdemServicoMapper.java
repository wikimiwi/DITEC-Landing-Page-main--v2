package com.ditec.assistencia.service;

import com.ditec.assistencia.dto.os.OrdemServicoRastreioResponse;
import com.ditec.assistencia.dto.os.OrdemServicoResponse;
import com.ditec.assistencia.dto.os.TimelineItemResponse;
import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.entity.TimelineOS;
import com.ditec.assistencia.enums.StatusOS;
import com.ditec.assistencia.enums.TipoUsuario;

import java.time.LocalDate;
import java.util.List;

final class OrdemServicoMapper {

    private OrdemServicoMapper() {
    }

    /**
     * Visao completa (dono/tecnico/admin). timeline == null => listagem resumida
     * (o historico e' carregado no detalhe, evitando N+1 nas listas).
     * incluirInternos=false (cliente) remove anotacoes internas do historico.
     */
    static OrdemServicoResponse toResponse(OrdemServico os, List<TimelineOS> timeline, boolean incluirInternos,
                                           boolean incluirTransicoes) {
        var ag = os.getAgendamento();
        List<String> transicoes = incluirTransicoes
                ? os.getStatus().proximos().stream().map(StatusOS::name).toList()
                : List.of();
        return new OrdemServicoResponse(
                os.getId(),
                os.getProtocolo(),
                ag.getId(),
                ag.getStatus().name(),
                nomeCliente(os),
                ag.getTelefoneExibicao(),
                os.getTecnico() != null ? os.getTecnico().getId() : null,
                os.getTecnico() != null ? os.getTecnico().getNome() : null,
                ag.getTipoAparelho(),
                ag.getBairro(),
                ag.getEndereco(),
                ag.isPrioridadeGas(),
                ag.getDataHora(),
                os.getDescricaoProblema(),
                os.getDiagnostico(),
                os.getValorOrcamento(),
                os.getOrcamentoAprovado(),
                os.getPrevisaoConclusao(),
                os.getStatus().name(),
                os.getStatus().getDescricao(),
                os.getStatus().getEtapa(),
                transicoes,
                os.getValor(),
                os.getFormaPagamento(),
                os.getDesconto(),
                os.getPecasUtilizadas(),
                os.getDescricaoFinal(),
                os.getCriadoEm(),
                os.getAtualizadoEm(),
                os.getDataInicio(),
                os.getDataConclusao(),
                os.getDataEntrega(),
                os.getGarantiaDias(),
                garantiaAte(os),
                os.getNotaFiscalNumero(),
                timeline == null ? null : mapTimeline(timeline, incluirInternos, false)
        );
    }

    /** Versao PUBLICA (protocolo + telefone) — ver OrdemServicoRastreioResponse. */
    static OrdemServicoRastreioResponse toRastreioResponse(OrdemServico os, List<TimelineOS> timeline) {
        var ag = os.getAgendamento();
        return new OrdemServicoRastreioResponse(
                os.getProtocolo(),
                abreviarNome(nomeCliente(os)),
                ag.getTipoAparelho(),
                os.getDescricaoProblema(),
                os.getStatus().name(),
                os.getStatus().getDescricao(),
                os.getStatus().getEtapa(),
                os.getCriadoEm(),
                os.getAtualizadoEm(),
                ag.getDataHora(),
                os.getDiagnostico(),
                os.getValorOrcamento(),
                os.getOrcamentoAprovado(),
                os.getTecnico() != null ? os.getTecnico().getNome() : null,
                os.getPrevisaoConclusao(),
                os.getDataConclusao(),
                os.getDataEntrega(),
                os.getGarantiaDias(),
                garantiaAte(os),
                mapTimeline(timeline, false, true)
        );
    }

    /** Garantia de 90 dias contada a partir da conclusao do servico. */
    static LocalDate garantiaAte(OrdemServico os) {
        if (os.getDataConclusao() == null || os.getGarantiaDias() == null) return null;
        return os.getDataConclusao().toLocalDate().plusDays(os.getGarantiaDias());
    }

    private static List<TimelineItemResponse> mapTimeline(List<TimelineOS> timeline, boolean incluirInternos,
                                                          boolean publico) {
        return timeline.stream()
                .filter(t -> incluirInternos || t.isVisivelCliente())
                .map(t -> new TimelineItemResponse(
                        t.getTipo().name(),
                        t.getStatusAnterior(),
                        t.getStatusNovo(),
                        descricaoStatus(t.getStatusNovo()),
                        t.getObservacao(),
                        publico ? responsavelPublico(t) : t.getAlteradoPor(),
                        t.isVisivelCliente(),
                        t.getDataHora()))
                .toList();
    }

    /** No rastreamento publico, o nome de um cliente nunca aparece por extenso. */
    private static String responsavelPublico(TimelineOS t) {
        if (t.getUsuario() == null) return "Sistema DITEC";
        if (t.getUsuario().getTipo() == TipoUsuario.CLIENTE) return "Cliente";
        if (t.getUsuario().getTipo() == TipoUsuario.ADMINISTRADOR) return "Equipe DITEC";
        return t.getAlteradoPor();
    }

    private static String descricaoStatus(String status) {
        if (status == null) return null;
        try {
            return StatusOS.valueOf(status).getDescricao();
        } catch (IllegalArgumentException ex) {
            return status;
        }
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
