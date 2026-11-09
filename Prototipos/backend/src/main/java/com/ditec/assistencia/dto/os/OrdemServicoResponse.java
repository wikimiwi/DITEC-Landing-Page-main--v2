package com.ditec.assistencia.dto.os;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Visao completa da OS — dono (cliente), tecnico atribuido ou administrador. */
public record OrdemServicoResponse(
        Long id,
        String protocolo,
        Long agendamentoId,
        String agendamentoStatus,
        String clienteNome,
        String clienteTelefone,
        Long tecnicoId,
        String tecnicoNome,
        String tipoAparelho,
        String bairro,
        String endereco,
        boolean prioridadeGas,
        LocalDateTime dataAgendada,
        String descricaoProblema,
        String diagnostico,
        BigDecimal valorOrcamento,
        Boolean orcamentoAprovado,
        LocalDate previsaoConclusao,
        String status,
        String statusDescricao,
        int etapa,
        List<String> transicoesPermitidas,
        BigDecimal valor,
        String formaPagamento,
        BigDecimal desconto,
        String pecasUtilizadas,
        String descricaoFinal,
        LocalDateTime dataAbertura,
        LocalDateTime ultimaAtualizacao,
        LocalDateTime dataInicio,
        LocalDateTime dataConclusao,
        LocalDateTime dataEntrega,
        Integer garantiaDias,
        LocalDate garantiaAte,
        String notaFiscalNumero,
        List<TimelineItemResponse> timeline
) {
}
