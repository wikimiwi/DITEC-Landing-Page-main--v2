package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrcamentoRespostaRequest(
        @NotNull(message = "Informe se o orcamento foi aprovado.") Boolean aprovado,
        @Size(max = 500, message = "Observacao muito longa.") String observacao
) {
}
