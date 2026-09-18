package com.ditec.assistencia.dto.os;

import java.util.List;

/**
 * Resposta do rastreamento PUBLICO (GET /api/ordens-servico/protocolo/{protocolo}).
 *
 * DECISAO DE SEGURANCA (auditoria): o protocolo e' um identificador sequencial e
 * publico por design (o cliente rastreia sem precisar logar, como um rastreio de
 * encomenda) — mas isso o torna adivinhavel/enumeravel. Antes desta correcao, esse
 * endpoint devolvia o MESMO objeto completo usado pelo dono/admin/tecnico, incluindo
 * nome completo do cliente, valor cobrado, forma de pagamento, desconto, pecas
 * utilizadas e numero da nota fiscal — ou seja, qualquer pessoa testando
 * "DITEC-2026-000001", "000002", "000003"... conseguia ver dados financeiros e
 * pessoais de terceiros. Este DTO existe para conter apenas o necessario para a
 * finalidade de rastreamento, sem esses dados sensiveis. O nome do cliente e'
 * abreviado (primeiro nome + inicial do ultimo sobrenome), igual ao padrao que a
 * propria landing page ja usava nos dados de exemplo antes desta integracao.
 */
public record OrdemServicoRastreioResponse(
        String protocolo,
        String clienteNomeAbreviado,
        String tecnicoNome,
        String tipoAparelho,
        String bairro,
        String status,
        Integer garantiaDias,
        List<TimelineItemResponse> timeline
) {
}
