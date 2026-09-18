package com.ditec.assistencia.dto.agendamento;

import java.time.LocalDateTime;

public record AgendamentoResponse(
        Long id,
        String protocolo,
        String nome,
        String telefone,
        String tipoAparelho,
        String descricaoProblema,
        String endereco,
        String bairro,
        LocalDateTime dataHora,
        String status,
        boolean prioridadeGas,
        String tecnicoNome,
        boolean contaVinculada,
        boolean elegivelParaAlteracao
) {
}
