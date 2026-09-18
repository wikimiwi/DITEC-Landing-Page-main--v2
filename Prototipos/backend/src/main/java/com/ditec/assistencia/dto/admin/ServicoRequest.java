package com.ditec.assistencia.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServicoRequest(
        @NotBlank(message = "Informe o nome do servico.") @Size(max = 150, message = "Nome muito longo.") String nome,
        @Size(max = 500, message = "Descricao muito longa.") String descricao,
        @NotNull(message = "Informe o valor base.")
        @DecimalMin(value = "0.0", message = "O valor nao pode ser negativo.")
        @DecimalMax(value = "999999.99", message = "Valor invalido.") BigDecimal valorBase
) {
}
