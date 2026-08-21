package com.ditec.assistencia.dto.os;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FinalizarOSRequest(
        @NotNull(message = "Informe o valor cobrado.") @DecimalMin(value = "0.0", message = "O valor nao pode ser negativo.") BigDecimal valor,
        @NotBlank(message = "Informe a forma de pagamento.") String formaPagamento,
        String pecasUtilizadas,
        String descricaoFinal,
        String notaFiscalNumero
) {
}
