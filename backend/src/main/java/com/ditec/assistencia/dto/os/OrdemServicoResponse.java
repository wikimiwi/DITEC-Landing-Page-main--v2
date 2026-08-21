package com.ditec.assistencia.dto.os;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrdemServicoResponse(
        Long id,
        String protocolo,
        String clienteNome,
        String tecnicoNome,
        String tipoAparelho,
        String bairro,
        String descricaoProblema,
        String status,
        BigDecimal valor,
        String formaPagamento,
        BigDecimal desconto,
        String pecasUtilizadas,
        String descricaoFinal,
        LocalDateTime dataInicio,
        LocalDateTime dataConclusao,
        Integer garantiaDias,
        String notaFiscalNumero,
        List<TimelineItemResponse> timeline
) {
}
