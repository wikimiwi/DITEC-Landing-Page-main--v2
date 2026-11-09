package com.ditec.assistencia.dto.os;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta do rastreamento PUBLICO (POST /api/ordens-servico/rastreio).
 *
 * Contem apenas o necessario para o cliente acompanhar o atendimento:
 * nunca inclui o ID interno, endereco, telefone, forma de pagamento, pecas,
 * nota fiscal ou anotacoes internas; o nome do cliente vem abreviado.
 * O valor do orcamento e' exibido porque o cliente precisa dele para
 * aprovar o servico — e a consulta ja exige protocolo + telefone.
 */
public record OrdemServicoRastreioResponse(
        String protocolo,
        String clienteNomeAbreviado,
        String tipoAparelho,
        String descricaoProblema,
        String status,
        String statusDescricao,
        int etapa,
        LocalDateTime dataAbertura,
        LocalDateTime ultimaAtualizacao,
        LocalDateTime dataAgendada,
        String diagnostico,
        BigDecimal valorOrcamento,
        Boolean orcamentoAprovado,
        String tecnicoNome,
        LocalDate previsaoConclusao,
        LocalDateTime dataConclusao,
        LocalDateTime dataEntrega,
        Integer garantiaDias,
        LocalDate garantiaAte,
        List<TimelineItemResponse> timeline
) {
}
