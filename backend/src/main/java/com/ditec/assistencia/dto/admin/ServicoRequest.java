package com.ditec.assistencia.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServicoRequest(
        @NotBlank(message = "Informe o nome do servico.") String nome,
        String descricao,
        @NotNull(message = "Informe o valor base.") BigDecimal valorBase
) {
}
